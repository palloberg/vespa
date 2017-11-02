// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This moves expired failed nodes:
 * <ul>
 *     <li>To parked: If the node has known hardware failure, docker hosts are moved to parked only when all its
 *     children are already in parked
 *     <li>To dirty: If the node has failed less than 5 times OR the environment is dev, test or perf OR system is CD,
 *     as those environments have no protection against users running bogus applications, so
 *     we cannot use the node failure count to conclude the node has a failure.
 *     <li>Otherwise the node will remain in failed
 * </ul>
 * Failed nodes are typically given a long expiry time to enable us to manually moved them back to
 * active to recover data in cases where the node was failed accidentally.
 * <p>
 * The purpose of the automatic recycling to dirty + fail count is that nodes which were moved
 * to failed due to some undetected hardware failure will end up being failed again.
 * When that has happened enough they will not be recycled.
 * <p>
 * The Chef recipe running locally on the node may set the hardwareFailureDescription to avoid the node
 * being automatically recycled in cases where an error has been positively detected.
 *
 * @author bratseth
 */
public class FailedExpirer extends Expirer {

    private static final Logger log = Logger.getLogger(NodeRetirer.class.getName());
    private final NodeRepository nodeRepository;
    private final Zone zone;

    public FailedExpirer(NodeRepository nodeRepository, Zone zone, Clock clock, 
                         Duration failTimeout, JobControl jobControl) {
        super(Node.State.failed, History.Event.Type.failed, nodeRepository, clock, failTimeout, jobControl);
        this.nodeRepository = nodeRepository;
        this.zone = zone;
    }

    @Override
    protected void expire(List<Node> expired) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node recycleCandidate : expired) {
            if (recycleCandidate.status().hardwareFailureDescription().isPresent() || recycleCandidate.status().hardwareDivergence().isPresent()) {
                List<String> nonParkedChildren = recycleCandidate.type() != NodeType.host ? Collections.emptyList() :
                        nodeRepository.getChildNodes(recycleCandidate.hostname()).stream()
                                .filter(node -> node.state() != Node.State.parked)
                                .map(Node::hostname)
                                .collect(Collectors.toList());

                if (nonParkedChildren.isEmpty()) {
                    nodeRepository.park(recycleCandidate.hostname(), Agent.system, "Parked by FailedExpirer due to HW failure/divergence on node");
                } else {
                    log.info(String.format("Expired failed node %s with HW failure/divergence is not parked because some of its children" +
                            " (%s) are not yet parked", recycleCandidate.hostname(), String.join(", ", nonParkedChildren)));
                }
            } else if (! failCountIndicatesHwFail(zone, recycleCandidate) || recycleCandidate.status().failCount() < 5) {
                nodesToRecycle.add(recycleCandidate);
            }
        }
        nodeRepository.setDirty(nodesToRecycle);
    }

    private boolean failCountIndicatesHwFail(Zone zone, Node node) {
        if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER) return false;
        return zone.environment() == Environment.prod || zone.environment() == Environment.staging;
    }

}
