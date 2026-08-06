#!/usr/bin/env bash
# Destroy everything provision.sh made. Run it when you are done — nothing here
# stops on its own, and two c7g.4xlarge left running is about $28 a day.
#
#   KARATE_PROFILING_ENV=~/private/.env etc/ec2/teardown.sh
#
# Elastic IPs are DISASSOCIATED, never released: they are pre-provisioned account
# resources this bench borrows, and releasing one loses the address for good.
source "$(dirname "$0")/lib.sh"

ids=()
for name in "$KP_INJECTOR_NAME" "$KP_MOCK_NAME"; do
    id="$(kp_id_of "$name")"
    [[ -n "$id" ]] && ids+=("$id")
done

for alloc in "${KP_EIP_INJECTOR:-}" "${KP_EIP_MOCK:-}"; do
    [[ -n "$alloc" ]] || continue
    assoc="$(aws ec2 describe-addresses --allocation-ids "$alloc" \
        --query 'Addresses[0].AssociationId' --output text 2>/dev/null | grep -v '^None$' || true)"
    if [[ -n "$assoc" ]]; then
        log "disassociating $alloc"
        aws ec2 disassociate-address --association-id "$assoc" >/dev/null || true
    fi
done

if ((${#ids[@]})); then
    log "terminating ${ids[*]}"
    aws ec2 terminate-instances --instance-ids "${ids[@]}" >/dev/null
    log "waiting for termination"
    aws ec2 wait instance-terminated --instance-ids "${ids[@]}"
else
    log "no instances to terminate"
fi

# The placement group will not delete while it still holds instances, and the
# security group will not delete while an ENI references it — both resolve once
# termination completes, but AWS can lag behind the waiter, so retry rather than
# leaving litter that blocks the next provision.
for attempt in 1 2 3 4 5 6; do
    if aws ec2 delete-placement-group --group-name "$KP_PG_NAME" 2>/dev/null; then
        log "deleted placement group"
        break
    fi
    aws ec2 describe-placement-groups --group-names "$KP_PG_NAME" >/dev/null 2>&1 || { log "placement group already gone"; break; }
    sleep 10
done

sg_id="$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$KP_SG_NAME" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null | grep -v '^None$' || true)"
if [[ -n "$sg_id" ]]; then
    for attempt in 1 2 3 4 5 6; do
        if aws ec2 delete-security-group --group-id "$sg_id" 2>/dev/null; then
            log "deleted security group $sg_id"
            break
        fi
        sleep 10
    done
fi

log "checking nothing is left running"
aws ec2 describe-instances \
    --filters "Name=tag:$KP_PREFIX,Values=true" "Name=instance-state-name,Values=running,pending" \
    --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name}' --output table
