#!/usr/bin/env bash
# Create the two-host bench: security group, cluster placement group, two
# instances, and (optionally) elastic IPs. Idempotent — safe to re-run.
#
#   KARATE_PROFILING_ENV=~/private/.env etc/ec2/provision.sh
#
# Costs money from the moment it returns. etc/ec2/teardown.sh removes everything
# it made, and nothing here has an auto-stop: see docs/PROFILING_EC2.md.
source "$(dirname "$0")/lib.sh"

# A soak needs time and one JVM, not the two-host parity topology — and an idle
# second instance overnight is real money for no measurement. --single launches
# the injector only; matrix.sh will refuse to run without a mock host, which is
# the correct failure.
single=false
[[ "${1:-}" == "--single" ]] && single=true

my_ip="$(curl -s --max-time 10 https://checkip.amazonaws.com || true)"
[[ -n "$my_ip" ]] || die "could not determine your public IP for the ssh rule"

# --- security group ----------------------------------------------------------
sg_id="$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$KP_SG_NAME" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null | grep -v '^None$' || true)"
if [[ -z "$sg_id" ]]; then
    log "creating security group $KP_SG_NAME"
    sg_id="$(aws ec2 create-security-group --group-name "$KP_SG_NAME" \
        --description "karate profiling bench" --vpc-id "$KP_VPC" \
        --query GroupId --output text)"
    # ssh from here only. The mock port is deliberately NOT opened to the world:
    # the injector reaches it on the private address, and an instrumented mock
    # that anyone can drive is an instrument whose window anyone can corrupt.
    aws ec2 authorize-security-group-ingress --group-id "$sg_id" \
        --protocol tcp --port 22 --cidr "${my_ip}/32" >/dev/null
    # Everything within the group: this is the injector-to-mock path.
    aws ec2 authorize-security-group-ingress --group-id "$sg_id" \
        --protocol -1 --source-group "$sg_id" >/dev/null
    log "security group $sg_id"
else
    log "security group $KP_SG_NAME exists ($sg_id)"
    # The operator's address changes with the network they are on, and a stale
    # rule is the most common reason a re-run cannot ssh to hosts it just made.
    if ! aws ec2 describe-security-groups --group-ids "$sg_id" \
            --query 'SecurityGroups[0].IpPermissions[?FromPort==`22`].IpRanges[].CidrIp' \
            --output text | grep -qF "${my_ip}/32"; then
        log "adding ssh rule for ${my_ip}/32"
        aws ec2 authorize-security-group-ingress --group-id "$sg_id" \
            --protocol tcp --port 22 --cidr "${my_ip}/32" >/dev/null
    fi
fi

# --- placement group ---------------------------------------------------------
# Cluster strategy packs both instances onto one spine: low and, more to the
# point, *stable* inter-host latency. The whole experiment reads a sub-millisecond
# difference, so jitter in the path between the arms is not a background detail.
if ! aws ec2 describe-placement-groups --group-names "$KP_PG_NAME" >/dev/null 2>&1; then
    log "creating cluster placement group $KP_PG_NAME"
    aws ec2 create-placement-group --group-name "$KP_PG_NAME" --strategy cluster >/dev/null
else
    log "placement group $KP_PG_NAME exists"
fi

# --- instances ---------------------------------------------------------------
launch() {
    local name="$1"
    local existing
    existing="$(kp_id_of "$name")"
    if [[ -n "$existing" ]]; then
        log "$name exists ($existing)"
        return
    fi
    log "launching $name"
    aws ec2 run-instances \
        --image-id "$KP_AMI" --instance-type "$KP_INSTANCE_TYPE" \
        --key-name "$KP_KEY_NAME" --security-group-ids "$sg_id" \
        --subnet-id "$KP_SUBNET" --placement "GroupName=$KP_PG_NAME,AvailabilityZone=$KP_AZ" \
        --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=40,VolumeType=gp3,DeleteOnTermination=true}' \
        --metadata-options 'HttpTokens=required,HttpEndpoint=enabled' \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$name},{Key=$KP_PREFIX,Value=true}]" \
        --query 'Instances[0].InstanceId' --output text >/dev/null
}

launch "$KP_INJECTOR_NAME"
$single || launch "$KP_MOCK_NAME"

log "waiting for instances to run"
if $single; then
    aws ec2 wait instance-running --instance-ids "$(kp_id_of "$KP_INJECTOR_NAME")"
else
    aws ec2 wait instance-running --instance-ids "$(kp_id_of "$KP_INJECTOR_NAME")" "$(kp_id_of "$KP_MOCK_NAME")"
fi

# --- elastic IPs (optional) --------------------------------------------------
associate_eip() {
    local alloc="$1" name="$2" instance
    [[ -n "$alloc" ]] || return 0
    instance="$(kp_id_of "$name")"
    local current
    current="$(aws ec2 describe-addresses --allocation-ids "$alloc" \
        --query 'Addresses[0].InstanceId' --output text 2>/dev/null | grep -v '^None$' || true)"
    if [[ "$current" == "$instance" ]]; then
        log "$name already holds $alloc"
        return 0
    fi
    log "associating $alloc with $name"
    aws ec2 associate-address --allocation-id "$alloc" --instance-id "$instance" >/dev/null
}
associate_eip "${KP_EIP_INJECTOR:-}" "$KP_INJECTOR_NAME"
$single || associate_eip "${KP_EIP_MOCK:-}" "$KP_MOCK_NAME"

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"

log "waiting for ssh"
kp_wait_ssh "$injector_ip"
$single || kp_wait_ssh "$mock_ip"

cat <<EOF

  injector : $injector_ip   ($(kp_id_of "$KP_INJECTOR_NAME"))
  mock     : ${mock_ip:-(not launched — --single)}   ${mock_private:+private $mock_private}

  ssh -i $KP_KEY_FILE $KP_SSH_USER@$injector_ip

  next: etc/ec2/bootstrap.sh        (jdk, build, sysctls — both hosts)
  then: etc/ec2/matrix.sh --tier 10ms --pairs 3
  done: etc/ec2/teardown.sh         (nothing stops on its own)
EOF
