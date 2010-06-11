package org.lilycms.rowlog.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

public class RowLogMessageConsumerExecutionState {

	private final byte[] messageId;
	Map<Integer, Boolean> states = new HashMap<Integer, Boolean>();
	Map<Integer, byte[]> locks = new HashMap<Integer, byte[]>();

	public RowLogMessageConsumerExecutionState(byte[] messageId) {
		this.messageId = messageId;
    }
	
	public void setState(int consumerId, boolean state) {
		states.put(consumerId, state);
    }
	
	public boolean getState(int consumerId) {
		Boolean state = states.get(consumerId);
		if (state == null) return true;
		return state;
	}
	
	public void setLock(int consumerId, byte[] flag) {
		locks.put(consumerId, flag);
	}
	
	public byte[] getLock(int consumerId) {
		return locks.get(consumerId);
	}

	public byte[] toBytes() {
		JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode object = factory.objectNode();

        object.put("id", messageId);
        
        ArrayNode consumerStatesNode = object.putArray("states");
        for (Entry<Integer, Boolean> entry : states.entrySet()) {
        	ObjectNode consumerStateNode = factory.objectNode();
        	consumerStateNode.put("id", entry.getKey());
        	consumerStateNode.put("state", entry.getValue());
        	consumerStatesNode.add(consumerStateNode);
        }

        ArrayNode consumerLocksNode = object.putArray("locks");
        for (Entry<Integer, byte[]> entry : locks.entrySet()) {
        	ObjectNode consumerLockNode = factory.objectNode();
        	consumerLockNode.put("id", entry.getKey());
        	consumerLockNode.put("lock", entry.getValue());
        	consumerLocksNode.add(consumerLockNode);
        }
        
        return toJsonBytes(object);
	}
	
	public byte[] toJsonBytes(JsonNode jsonNode) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(os, jsonNode);
        } catch (IOException e) {
            // Small chance of this happening, since we are writing to a byte array
            throw new RuntimeException(e);
        }
        return os.toByteArray();
    }
	
	public static RowLogMessageConsumerExecutionState fromBytes(byte[] bytes) throws IOException {
		
		JsonNode node = new ObjectMapper().readValue(bytes, 0, bytes.length, JsonNode.class);
		RowLogMessageConsumerExecutionState executionState = new RowLogMessageConsumerExecutionState(node.get("id").getBinaryValue());
		
		JsonNode consumerStatesNode = node.get("states");
		for (int i = 0; i < consumerStatesNode.size(); i++) {
			JsonNode consumerStateNode = consumerStatesNode.get(i);
			Integer id = consumerStateNode.get("id").getIntValue();
			Boolean state = consumerStateNode.get("state").getBooleanValue();
			executionState.setState(id, state);
		}

		JsonNode consumerLocksNode = node.get("locks");
		for (int i = 0; i < consumerLocksNode.size(); i++) {
			JsonNode consumerLockNode = consumerLocksNode.get(i);
			Integer id = consumerLockNode.get("id").getIntValue();
			byte[] lock = consumerLockNode.get("lock").getBinaryValue();
			executionState.setLock(id, lock);
		}
		
		return executionState;
	}

	public boolean allDone() {
		for (Boolean consumerDone : states.values()) {
	        if (!consumerDone)
	        	return false;
        }
		return true;
	}
}
