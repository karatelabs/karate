#!/usr/bin/env bash
# Destroy everything provision.sh made. Run it when you are done — nothing here
# stops on its own, and two c7g.4xlarge left running is about $28 a day.
#
#   KARATE_PROFILING_ENV=~/private/.env etc/ec2/teardown.sh
#   ... --force   skip the "you have uncollected results" guard
#
# Elastic IPs are DISASSOCIATED, never released: they are pre-provisioned account
# resources this bench borrows, and releasing one loses the address for good.
source "$(dirname "$0")/lib.sh"

# By tag, not by name, and including stopped instances: a stopped one still bills its
# volume and still blocks the security group, and a name lookup that filters on
# "running" reports a clean teardown while it sits there.
ids=()
while read -r id; do [[ -n "$id" ]] && ids+=("$id"); done <<< "$(kp_all_alive)"

# Digests live only on the injector until collect.sh pulls them, and terminating the
# instance destroys them with no way back. A matrix is ~20 minutes and $0.40 of machine
# time; re-running one because teardown ran first is the single most annoying way to lose
# work here, and nothing used to say a word about it.
if ((${#ids[@]} > 0)) && [[ "${1:-}" != "--force" ]]; then
    injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME" 2>/dev/null || true)"
    if [[ -n "$injector_ip" ]]; then
        # Note what is NOT done here: `|| echo 0`. This used to fall back to zero when the ssh
        # failed, which made the guard fail OPEN — and the most common reason it fails is a stale
        # security-group rule after your public IP changed, which is exactly the moment you still
        # have results on the box. "Could not ask" is not "nothing to lose", so it refuses.
        if ! uncollected="$(kp_ssh "$injector_ip" \
                "ls -1d ~/karate/karate-profiling/target/profiling/*/ 2>/dev/null | wc -l" 2>/dev/null)"; then
            log "!! cannot reach the injector to check for uncollected runs."
            log "   Refusing rather than guessing: if it holds results, terminating destroys them."
            log "   Re-run etc/ec2/provision.sh to refresh the ssh rule (your IP may have changed),"
            log "   or re-run this with --force if you do not want whatever is on it."
            exit 1
        fi
        if [[ "${uncollected:-0}" -gt 0 ]]; then
            log "!! the injector still holds $uncollected run director(ies)."
            log "   Terminating destroys them. The sequence is:"
            log "     1. etc/ec2/collect.sh"
            log "     2. check the digests are actually in \$KP_RESULTS"
            log "     3. etc/ec2/teardown.sh --force"
            log "   --force is needed even after collecting: collect.sh copies, it does not delete,"
            log "   so this count stays above zero and only you can say the copy is good."
            exit 1
        fi
    fi
fi

if ((${#ids[@]} == 0)); then
    # The most expensive failure this script has is doing nothing and saying so
    # cheerfully — which is exactly what happens if KP_PREFIX changed since provision,
    # because then nothing matches the tag and every lookup comes back empty.
    log "no instances tagged $KP_PREFIX — if you changed KP_PREFIX since provisioning,"
    log "  this teardown will NOT find the old ones. Check by hand:"
    log "  aws ec2 describe-instances --filters Name=instance-state-name,Values=running \\"
    log "    --query 'Reservations[].Instances[].{Id:InstanceId,Type:InstanceType,Name:Tags[?Key==\`Name\`]|[0].Value}' --output table"
fi

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

# Every non-terminated state, so a stopped leftover is visible rather than filtered out.
log "checking nothing is left"
aws ec2 describe-instances \
    --filters "Name=tag:$KP_PREFIX,Values=true" \
    "Name=instance-state-name,Values=running,pending,stopping,stopped,shutting-down" \
    --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name}' --output table
