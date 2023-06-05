package org.lbee.models.messages;

import org.lbee.instrumentation.TraceField;

public class RequestMessage extends Message {

        @TraceField(name="mlastLogTerm")
        protected final long lastLogTerm;
        @TraceField(name="mlastLogIndex")
        protected final long lastLogIndex;

        public RequestMessage(String from, String to, MessageType type, long term, long lastLogTerm, long lastLogIndex, long senderClock) {
                super(from, to, type, term, senderClock);
                this.lastLogTerm = lastLogTerm;
                this.lastLogIndex = lastLogIndex;
        }

        public long getLastLogTerm() {
                return lastLogTerm;
        }

        public long getLastLogIndex() {
                return lastLogIndex;
        }
}
