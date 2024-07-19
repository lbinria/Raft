package io.microraft.impl;

import io.microraft.impl.local.LocalRaftGroup;
import io.microraft.test.util.BaseTest;
import org.junit.After;
import org.junit.Test;
import org.lbee.instrumentation.clock.ClockException;

import static io.microraft.impl.local.SimpleStateMachine.applyValue;

import java.io.IOException;

public class AppendEntryValidationTest extends BaseTest {

    private LocalRaftGroup group;

    @After
    public void destroy() {
        if (group != null) {
            group.destroy();
        }
    }

    @Test
    public void appendEntries() {
        group = LocalRaftGroup.start(3);
        RaftNodeImpl leader = group.waitUntilLeaderElected();
        leader.replicate(applyValue("v_3")).join();
        leader.replicate(applyValue("v_2")).join();
        leader.replicate(applyValue("v_1")).join();
        RaftNodeImpl follower = group.getAnyNodeExcept(leader.getLocalEndpoint());
        leader.transferLeadership(follower.getLocalEndpoint()).join();
        // leader = group.waitUntilLeaderElected();
        // leader.replicate(applyValue("v_6")).join();
    }

}
