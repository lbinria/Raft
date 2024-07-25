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

import static io.microraft.RaftRole.CANDIDATE;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.lbee.instrumentation.trace.TLATracer;
import org.lbee.instrumentation.trace.VirtualField;
//import io.microraft.impl.util.SpecHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microraft.RaftRole;
import io.microraft.impl.RaftNodeImpl;
import io.microraft.impl.state.CandidateState;
import io.microraft.impl.state.RaftState;
import io.microraft.model.message.VoteRequest;
import io.microraft.model.message.VoteResponse;
import io.microraft.statemachine.StateMachine;

/**
 * Handles a {@link VoteResponse} sent for a {@link VoteRequest}.
 * <p>
 * Changes the local Raft node's role to {@link RaftRole#LEADER} via
 * {@link RaftState#toLeader()} if the majority votes has been granted for this
 * term.
 * <p>
 * In the beginning of the new term, the Raft group leader appends a new log
 * entry that contains an operation which is returned via
 * {@link StateMachine#getNewTermOperation()}.
 * <p>
 * See <i>5.2 Leader election</i> section of <i>In Search of an Understandable
 * Consensus Algorithm</i> paper by <i>Diego Ongaro</i> and <i>John
 * Ousterhout</i>.
 *
 * @see VoteRequest
 * @see VoteResponse
 */
public class VoteResponseHandler extends AbstractResponseHandler<VoteResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoteResponseHandler.class);

    public final TLATracer tracer;

    private final VirtualField traceTerm;

    private final VirtualField traceRole;

    public VoteResponseHandler(RaftNodeImpl raftNode, VoteResponse response, TLATracer tracer) {
        super(raftNode, response);

        this.tracer = tracer;

        this.traceRole = tracer.getVariableTracer("role");
        this.traceTerm = tracer.getVariableTracer("term");
    }

    @Override
    protected void handleResponse(@Nonnull VoteResponse response) {
        // Benjamin: Note spec has a guard /\ m.mterm = currentTerm[i]
        // If condition didn't match, don't commit HandleRequestVoteResponse !

        if (state.role() != CANDIDATE) {
            LOGGER.debug("{} Ignored {}. We are not CANDIDATE anymore.", localEndpointStr(), response);
            // TLA: nothing to do
            return;
        } else if (response.getTerm() > state.term()) {
            // If the response term is greater than the local term, update the local term
            // and convert to follower (§5.1)
            LOGGER.info("{} Moving to new term: {} from current term: {} after {}", localEndpointStr(),
                    response.getTerm(), state.term(), response);
            // TLA: to follower with update term
            node.toFollower(response.getTerm());
            // SpecHelper.commitChanges(node.getSpec(), "UpdateTerm");

            try {
                this.traceRole.getField(localEndpointStr()).update("follower");
                this.traceTerm.getField(localEndpointStr()).update(response.getTerm());
                tracer.log("UpdateTerm", new Object[]{localEndpointStr()});
            } catch (IOException e) {
                e.printStackTrace();
            }

            return;
        } else if (response.getTerm() < state.term()) {
            LOGGER.warn("{} Stale {} is received, current term: {}", localEndpointStr(), response, state.term());
            // TLA: nothing to do
            return;
        }

        CandidateState candidateState = state.candidateState();
        if (response.isGranted() && candidateState.grantVote(response.getSender())) {
            /*
             * node.getSpec().getVariable("votesGranted").getField(localEndpoint().getId().
             * toString()) .add(response.getSender().getId().toString());
             */
            LOGGER.info("{} Vote granted from {} for term: {}, number of votes: {}, majority: {}", localEndpointStr(),
                    response.getSender().getId(), state.term(), candidateState.voteCount(), candidateState.majority());
        }
        // TLA: No variable equivalent for votesResponded in Microraft implementation
        /*
         * node.getSpec().getVariable("votesResponded").getField(localEndpoint().getId()
         * .toString()) .add(response.getSender().getId().toString());
         */

        // SpecHelper.commitChanges(node.getSpec(), "HandleRequestVoteResponse");

        /*
         * try { this.tracer.log("HandleRequestVoteResponse"); } catch (IOException e) {
         * e.printStackTrace(); }
         */

        if (candidateState.isMajorityGranted()) {
            LOGGER.info("{} We are the LEADER!", localEndpointStr());
            node.toLeader();
        }
    }

}
