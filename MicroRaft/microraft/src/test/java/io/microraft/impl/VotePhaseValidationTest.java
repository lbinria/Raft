package io.microraft.impl;

import io.microraft.impl.local.LocalRaftGroup;
import io.microraft.test.util.BaseTest;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.lbee.instrumentation.clock.ClockException;

public class VotePhaseValidationTest extends BaseTest {

    private LocalRaftGroup group;

    @After
    public void destroy() {
        if (group != null) {
            group.destroy();
        }
    }

    @Test
    public void electLeaders() {
        group = LocalRaftGroup.start(3);
        RaftNodeImpl leader = group.waitUntilLeaderElected();

        // Repeat
        for (int i = 0; i < 3; i++) {
            System.out.printf("Pass %s.\n", i);
            RaftNodeImpl follower = group.getAnyNodeExcept(leader.getLocalEndpoint());
            leader.transferLeadership(follower.getLocalEndpoint()).join();
            leader = group.waitUntilLeaderElected();
        }
    }

}
