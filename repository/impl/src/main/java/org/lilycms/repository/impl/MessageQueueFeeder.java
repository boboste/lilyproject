/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.repository.impl;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.lilycms.rowlog.api.RowLog;
import org.lilycms.rowlog.api.RowLogException;
import org.lilycms.rowlog.api.RowLogMessage;
import org.lilycms.rowlog.api.RowLogMessageListener;
import org.lilycms.rowlog.api.RowLogShard;
import org.lilycms.rowlog.impl.RowLogImpl;
import org.lilycms.rowlog.impl.RowLogShardImpl;

public class MessageQueueFeeder implements RowLogMessageListener {
    public static Configuration configuration;
    private RowLog messageQueue = null;
    public MessageQueueFeeder(RowLog messageQueue) {
        this.messageQueue = messageQueue;
    }
    
    public MessageQueueFeeder() throws RowLogException, IOException {
        messageQueue = new RowLogImpl("MQ", HBaseTableUtil.getRecordTable(configuration), HBaseTableUtil.MQ_PAYLOAD_COLUMN_FAMILY,
                HBaseTableUtil.MQ_COLUMN_FAMILY, 10000L, configuration); 
        RowLogShard messageQueueShard = new RowLogShardImpl("MQS1", configuration, messageQueue, 100);
        messageQueue.registerShard(messageQueueShard);
    }
    
    public boolean processMessage(RowLogMessage message) {
        try {
            messageQueue.putMessage(message.getRowKey(), message.getData(), message.getPayload(), null);
            return true;
        } catch (RowLogException e) {
            return false;
        }
    }
}
