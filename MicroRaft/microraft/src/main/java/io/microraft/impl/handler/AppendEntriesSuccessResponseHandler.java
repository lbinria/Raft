/*
 * Original work Copyright (c) 2008-2020, Hazelcast, Inc.
 * Modified work Copyright (c) 2020, MicroRaft.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.microraft.impl.handler;

import io.microraft.RaftEndpoint;
import io.microraft.impl.RaftNodeImpl;
import io.microraft.impl.state.FollowerState;
import io.microraft.impl.state.LeaderState;
import io.microraft.impl.state.QueryState;
import io.microraft.impl.state.RaftState;
//import io.microraft.impl.util.SpecHelper;
import io.microraft.model.message.AppendEntriesFailureResponse;
import io.microraft.model.message.AppendEntriesRequest;
import io.microraft.model.message.AppendEntriesSuccessResponse;

import org.lbee.instrumentation.trace.TLATracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static io.microraft.RaftRole.LEADER;

import java.io.IOException;

/**
 * Handles an {@link AppendEntriesSuccessResponse} which can be sent as a
 * response to a append-entries request or an install-snapshot request.
 * <p>
 * Advances {@link RaftState#commitIndex()} according to the match indices of
 * the Raft group members.
 * <p>
 * See <i>5.3 Log replication</i> section of <i>In Search of an Understandable
 * Consensus Algorithm</i> paper by <i>Diego Ongaro</i> and <i>John
 * Ousterhout</i>.
 *
 * @see AppendEntriesRequest
 * @see AppendEntriesSuccessResponse
 * @see AppendEntriesFailureResponse
 */
public class AppendEntriesSuccessResponseHandler extends AbstractResponseHandler<AppendEntriesSuccessResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppendEntriesSuccessResponseHandler.class);

    public final TLATracer tracer;

    public AppendEntriesSuccessResponseHandler(RaftNodeImpl raftNode, AppendEntriesSuccessResponse response,
            TLATracer tracer) {
        super(raftNode, response);

        this.tracer = tracer;
    }

    @Override
    protected void handleResponse(@Nonnull AppendEntriesSuccessResponse response) {
        if (state.role() != LEADER) {
            LOGGER.warn("{} Ignored {}. We are not LEADER anymore.", localEndpointStr(), response);
            return;
        } else if (response.getTerm() > state.term()) {
            LOGGER.warn("{} Ignored invalid response {} for current term: {}", localEndpointStr(), response,
                    state.term());
            return;
        }

        LOGGER.debug("{} received {}.", localEndpointStr(), response);
        System.out.println("HandleAppendEntriesResponse");

        // Receiver
        String tla_i = localEndpoint().getId().toString();
        // Sender
        String tla_j = response.getSender().getId().toString();
        // Event args
        Object[] eventArgs = new Object[]{tla_i, tla_j};

        if (updateFollowerIndices(response)) {
            // SpecHelper.commitChanges(node.getSpec(), "HandleAppendEntriesResponse",
            // eventArgs);

            /*
             * try { this.tracer.log("HandleAppendEntriesResponse"); } catch (IOException e)
             * { e.printStackTrace(); }
             */
            if (!node.tryAdvanceCommitIndex()) {
                trySendAppendRequest(response);
            }
        } else {
            // SpecHelper.commitChanges(node.getSpec(), "HandleAppendEntriesResponse",
            // eventArgs);

            /*
             * try { this.tracer.log("HandleAppendEntriesResponse"); } catch (IOException e)
             * { e.printStackTrace(); }
             */

            node.tryRunQueries();
        }

        checkIfQueryAckNeeded(response);
    }

    private boolean updateFollowerIndices(AppendEntriesSuccessResponse response) {
        // If successful: update nextIndex and matchIndex for follower (§5.3)

        LeaderState leaderState = state.leaderState();
        RaftEndpoint follower = response.getSender();
        FollowerState followerState = leaderState.getFollowerStateOrNull(follower);

        if (followerState == null) {
            LOGGER.warn("{} follower/learner: {} not found for {}.", localEndpointStr(), follower.getId(), response);
            return false;
        }

        if (state.isVotingMember(follower)
                && leaderState.queryState().tryAck(response.getQuerySequenceNumber(), follower)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(localEndpointStr() + " ack from " + follower.getId() + " for query sequence number: "
                        + response.getQuerySequenceNumber());
            }
        }

        long matchIndex = followerState.matchIndex();
        long followerLastLogIndex = response.getLastLogIndex();

        followerState.responseReceived(response.getFlowControlSequenceNumber(), node.getClock().millis());

        if (followerLastLogIndex > matchIndex) {
            long newNextIndex = followerLastLogIndex + 1;
            followerState.matchIndex(followerLastLogIndex);
            // node.getSpec().getVariable("matchIndex").getField(node.getLocalEndpoint().getId().toString())
            // .getField(follower.getId().toString()).set(followerLastLogIndex);

            followerState.nextIndex(newNextIndex);

            // node.getSpec().getVariable("nextIndex").getField(node.getLocalEndpoint().getId().toString())
            // .getField(follower.getId().toString()).set(newNextIndex);

            System.out.println("HANDLE RESPONSE SUCCESS.");

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(localEndpointStr() + " Updated match index: " + followerLastLogIndex + " and next index: "
                        + newNextIndex + " for follower: " + follower.getId());
            }

            return true;
        } else if (followerLastLogIndex < matchIndex && LOGGER.isDebugEnabled()) {
            LOGGER.debug(localEndpointStr() + " Will not update match index for follower: " + follower.getId()
                    + ". follower last log index: " + followerLastLogIndex + ", match index: " + matchIndex);
        }

        return false;
    }

    private void checkIfQueryAckNeeded(AppendEntriesSuccessResponse response) {
        LeaderState leaderState = state.leaderState();
        if (leaderState == null) {
            // this can happen if this node was removed from the group when
            // the commit index was advanced.
            return;
        } else if (!state.isVotingMember(response.getSender())) {
            // learners are not part of the replication quorum.
            return;
        }

        QueryState queryState = leaderState.queryState();
        if (queryState.isAckNeeded(response.getSender(), state.logReplicationQuorumSize())) {
            node.sendAppendEntriesRequest(response.getSender());
        }
    }

    private void trySendAppendRequest(AppendEntriesSuccessResponse response) {
        long followerLastLogIndex = response.getLastLogIndex();
        if (state.log().lastLogOrSnapshotIndex() > followerLastLogIndex
                || state.commitIndex() == followerLastLogIndex) {
            // If the follower is still missing some log entries or has not learnt the
            // latest commit index yet,
            // then send another append request.
            node.sendAppendEntriesRequest(response.getSender());
        }
    }

}
