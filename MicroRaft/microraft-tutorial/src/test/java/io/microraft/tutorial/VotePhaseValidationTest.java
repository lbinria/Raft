package io.microraft.tutorial;

import io.microraft.RaftNode;
import io.microraft.impl.RaftNodeImpl;
import io.microraft.statemachine.StateMachine;
import io.microraft.tutorial.atomicregister.OperableAtomicRegister;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static io.microraft.RaftRole.FOLLOWER;
import static org.assertj.core.api.Assertions.assertThat;

public class VotePhaseValidationTest extends BaseLocalTest {
    @Override
    protected StateMachine createStateMachine() {
        return new OperableAtomicRegister();
    }

    @Test
    public void testVotePhase() throws InterruptedException {
        RaftNode leader = waitUntilLeaderElected();
        System.out.println("Leader elected.");
        leader.transferLeadership(raftNodes.get(0).getLocalEndpoint()).join();
        System.out.println("Leader terminate.");
        System.out.println("WAIT.");

        TimeUnit.SECONDS.sleep(5);

        leader = waitUntilLeaderElected();
        System.out.println("Leader elected.");
    }

}
