package org.lilycms.linkindex.test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableExistsException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lilycms.hbaseindex.IndexManager;
import org.lilycms.linkindex.FieldedLink;
import org.lilycms.linkindex.LinkIndex;
import org.lilycms.linkindex.LinkIndexUpdater;
import org.lilycms.queue.api.QueueMessage;
import org.lilycms.queue.mock.TestLilyQueue;
import org.lilycms.queue.mock.TestQueueMessage;
import org.lilycms.repository.api.*;
import org.lilycms.repository.impl.*;
import org.lilycms.repoutil.EventType;
import org.lilycms.repoutil.RecordEvent;
import org.lilycms.repoutil.VersionTag;
import org.lilycms.testfw.TestHelper;

public class LinkIndexTest {
    private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

    private TypeManager typeManager;
    private Repository repository;
    private IdGenerator ids;
    private LinkIndex linkIndex;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestHelper.setupLogging();
        TEST_UTIL.startMiniCluster(1);

        IndexManager.createIndexMetaTable(TEST_UTIL.getConfiguration());
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        TEST_UTIL.shutdownMiniCluster();
    }

    @Before
    public void init() throws Exception {
        IdGenerator idGenerator = new IdGeneratorImpl();
        typeManager = new HBaseTypeManager(idGenerator, TEST_UTIL.getConfiguration());
        BlobStoreAccess dfsBlobStoreAccess = new DFSBlobStoreAccess(TEST_UTIL.getDFSCluster().getFileSystem());
        SizeBasedBlobStoreAccessFactory blobStoreAccessFactory = new SizeBasedBlobStoreAccessFactory(dfsBlobStoreAccess);
        repository = new HBaseRepository(typeManager, idGenerator, blobStoreAccessFactory, TEST_UTIL.getConfiguration());
        ids = repository.getIdGenerator();
        IndexManager indexManager = new IndexManager(TEST_UTIL.getConfiguration());

        try {
            LinkIndex.createIndexes(indexManager);
        } catch (TableExistsException e) {
            // TODO better way to handle this?
        }
        linkIndex = new LinkIndex(indexManager, repository);
    }

    @Test
    public void testLinkIndex() throws Exception {
        Set<FieldedLink> links1 = new HashSet<FieldedLink>();
        links1.add(new FieldedLink(ids.newRecordId("id1"), "field1"));
        links1.add(new FieldedLink(ids.newRecordId("id2"), "field1"));

        Set<FieldedLink> links2 = new HashSet<FieldedLink>();
        links2.add(new FieldedLink(ids.newRecordId("id3"), "field1"));
        links2.add(new FieldedLink(ids.newRecordId("id4"), "field1"));

        linkIndex.updateLinks(ids.newRecordId("idA"), "live", links1);
        linkIndex.updateLinks(ids.newRecordId("idB"), "live", links1);
        linkIndex.updateLinks(ids.newRecordId("idC"), "live", links2);

        // Test forward link retrieval
        Set<FieldedLink> links = linkIndex.getForwardLinks(ids.newRecordId("idA"), "live");
        assertTrue(links.contains(new FieldedLink(ids.newRecordId("id1"), "field1")));
        assertTrue(links.contains(new FieldedLink(ids.newRecordId("id2"), "field1")));
        assertEquals(2, links.size());

        // Test backward link retrieval
        Set<RecordId> referrers = linkIndex.getReferrers(ids.newRecordId("id1"), "live");
        assertTrue(referrers.contains(ids.newRecordId("idA")));
        assertTrue(referrers.contains(ids.newRecordId("idB")));
        assertEquals(2, referrers.size());

        // Update the links for record idA and re-check
        links1.add(new FieldedLink(ids.newRecordId("id2a"), "field1"));
        linkIndex.updateLinks(ids.newRecordId("idA"), "live", links1);

        links = linkIndex.getForwardLinks(ids.newRecordId("idA"), "live");
        assertTrue(links.contains(new FieldedLink(ids.newRecordId("id1"), "field1")));
        assertTrue(links.contains(new FieldedLink(ids.newRecordId("id2"), "field1")));
        assertTrue(links.contains(new FieldedLink(ids.newRecordId("id2a"), "field1")));
        assertEquals(3, links.size());

        referrers = linkIndex.getReferrers(ids.newRecordId("id1"), "live");
        assertTrue(referrers.contains(ids.newRecordId("idA")));
        assertTrue(referrers.contains(ids.newRecordId("idB")));
        assertEquals(2, referrers.size());

        referrers = linkIndex.getReferrers(ids.newRecordId("id2a"), "live");
        assertTrue(referrers.contains(ids.newRecordId("idA")));
        assertEquals(1, referrers.size());
    }

    @Test
    public void testLinkIndexUpdater() throws Exception {
        TestLilyQueue queue = new TestLilyQueue();
        LinkIndexUpdater linkIndexUpdater = new LinkIndexUpdater(repository, typeManager, linkIndex, queue);

        FieldType nonVersionedFt = typeManager.newFieldType(typeManager.getValueType("LINK", false, false),
                new QName("ns", "link1"), Scope.NON_VERSIONED);
        nonVersionedFt = typeManager.createFieldType(nonVersionedFt);

        FieldType versionedFt = typeManager.newFieldType(typeManager.getValueType("LINK", true, false),
                new QName("ns", "link2"), Scope.VERSIONED);
        versionedFt = typeManager.createFieldType(versionedFt);

        FieldType versionedMutableFt = typeManager.newFieldType(typeManager.getValueType("LINK", true, false),
                new QName("ns", "link3"), Scope.VERSIONED_MUTABLE);
        versionedMutableFt = typeManager.createFieldType(versionedMutableFt);

        RecordType recordType = typeManager.newRecordType("MyRecordType");
        recordType.addFieldTypeEntry(typeManager.newFieldTypeEntry(nonVersionedFt.getId(), false));
        recordType.addFieldTypeEntry(typeManager.newFieldTypeEntry(versionedFt.getId(), false));
        recordType.addFieldTypeEntry(typeManager.newFieldTypeEntry(versionedMutableFt.getId(), false));
        recordType = typeManager.createRecordType(recordType);

        //
        // Link extraction from a record without versions
        //
        {
            Record record = repository.newRecord();
            record.setRecordType(recordType.getId(), null);
            record.setField(nonVersionedFt.getName(), new Link(ids.newRecordId("foo1")));
            record = repository.create(record);

            RecordEvent recordEvent = new RecordEvent();
            recordEvent.addUpdatedField(nonVersionedFt.getId());
            QueueMessage message = new TestQueueMessage(EventType.EVENT_RECORD_CREATED, record.getId(), recordEvent.toJsonBytes());
            queue.broadCastMessage(message);

            Set<RecordId> referrers = linkIndex.getReferrers(ids.newRecordId("foo1"), VersionTag.VERSIONLESS_TAG);
            assertEquals(1, referrers.size());
            assertTrue(referrers.contains(record.getId()));

            referrers = linkIndex.getReferrers(ids.newRecordId("bar1"), VersionTag.VERSIONLESS_TAG);
            assertEquals(0, referrers.size());

            // Now perform an update so that there is a version
            record.setField(versionedFt.getName(), Arrays.asList(new Link(ids.newRecordId("foo2")), new Link(ids.newRecordId("foo3"))));
            record = repository.update(record);

//            recordEvent = new RecordEvent();
//            recordEvent.setVersionCreated(record.getVersion());
//            recordEvent.addUpdatedField(versionedFt.getId());
//            message = new TestQueueMessage(EventType.EVENT_RECORD_UPDATED, record.getId(), recordEvent.toJsonBytes());
//            queue.broadCastMessage(message);
//
//            // Since there is a version but no vtag yet, there should be nothing in the link index for this record
//            referrers = linkIndex.getReferrers(ids.newRecordId("foo1"), VersionTag.VERSIONLESS_TAG);
//            assertEquals(0, referrers.size());
//
//            assertEquals(0, linkIndex.getAllForwardLinks(record.getId()).size());

        }
    }
}
