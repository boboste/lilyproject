package org.lilyproject.tools.tester;

import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.joda.time.DateTime;
import org.lilyproject.repository.api.RecordId;
import org.lilyproject.util.exception.StackTracePrinter;
import org.lilyproject.util.json.JsonUtil;

public abstract class AbstractTestAction implements TestAction {

    protected int count;
    protected String source;
    protected String destination;
    protected int failureCount = 0;
    private String name;
    protected JsonNode actionNode;
    protected TestActionContext testActionContext;

    public AbstractTestAction(JsonNode actionNode, TestActionContext testActionContext) {
        this.actionNode = actionNode;
        name = JsonUtil.getString(actionNode, "name");
        count = actionNode.get("count").getIntValue();
        source = JsonUtil.getString(actionNode, "source", null);
        destination = JsonUtil.getString(actionNode, "destination", null);
        this.testActionContext = testActionContext;
    }
    
    @Override
    public TestActionContext getContext() {
        return testActionContext;
    }
    
    @Override
    abstract public int run();
    
    protected void report(boolean success, double duration) {
        report(success, duration, null);
    }
    
    protected void report(boolean success, double duration, String subactionName) {
        report(success, duration, null, subactionName);
    }

    protected void report(boolean success, double duration, String actionType, String subactionName) {
        String metricname = name;
        if (subactionName != null) {
            metricname = name + "." + subactionName;
        }
        if (testActionContext.metrics != null) {
            testActionContext.metrics.increment(metricname, actionType, duration / 1e6d);
        }
    }

    protected void reportError(String message, Throwable throwable) {
        // Synchronize to avoid stacktraces from different threads running through each other
        synchronized (testActionContext.errorStream) {
            failureCount++;
            testActionContext.errorStream.println("[" + new DateTime() + "][" + Thread.currentThread().getName() + "] "
                    + message);
            StackTracePrinter.printStackTrace(throwable, testActionContext.errorStream);
            testActionContext.errorStream.println("---------------------------------------------------------------------------");
        }
        
        System.err.println("ATTENTION: an error occurred, check failure output file for details.");
    }
    
    protected TestRecord getNonDeletedRecord(Set<TestRecord> records) {
        if (records == null || records.size() == 0) {
            return null;
        }
        TestRecord[] testRecords = records.toArray(new TestRecord[records.size()]);

        TestRecord testRecord;
        int loopCnt = 0;
        do {
            int selectedIndex = (int) Math.floor(Math.random() * records.size());
            testRecord = testRecords[selectedIndex];
            loopCnt++;
            if ((loopCnt % 100) == 0) {
                System.out.println("Already tried " + loopCnt + " times to pick a non-deleted record.");
            }
        } while (testRecord.isDeleted());

        return testRecord;
    }

    @Override
    public abstract ActionResult linkFieldAction(TestFieldType testFieldType, RecordId recordId);
}
