package org.lilycms.repository.api;

import org.lilycms.util.ArgumentValidator;
import org.lilycms.util.ObjectUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * A link to another record.
 *
 * <p>The difference between a Link and a RecordId is that a Link can be relative. This means
 * that the link needs to be resolved against the context it occurs in (= the record it occurs in)
 * in order to get the actual id of the record it points to.
 *
 * <h2>About relative links</h2>
 *
 * <p>As an example, suppose we have a record with a variant property 'language=en'. In this
 * record, we want to link to another record, also having the 'language=en'. By using Link,
 * we can specify only the master record ID of the target record, without the 'language=en'
 * property. The 'language=en' will be inherited from the record in which the link occurs.
 * The advantage is that if the link is copied to another record with e.g. 'language=fr',
 * the link will automatically adjust to this context.
 *
 * <p>Links have the following possibilities to specify the relative link:
 *
 * <ul>
 *  <li>the master record id itself is optional, and can be copied (= inherited) from the context.
 *  <li>it is possible to specify that all variant properties from the context should be copied.
 *      This is specified by the "copyAll" property of this object.
 *  <li>it is possible to specify individual properties. For each individual property, you can
 *      specify one of the following:
 *      <ul>
 *       <li>an exact value
 *       <li>copy the value from the context (if any). This allows to selectively copy some variant
 *           properties from the context. This only makes sense when not using copyAll.
 *       <li>remove the value, if it would have been copied from the context. This is useful if you
 *           want to copy all variant properties from the context (not knowing how many there are),
 *           except some of them.
 *      </ul>
 * </ul>
 *
 * <h2>Creating links</h2>
 *
 * <p>If you just want a link to point to an exact record id, use the constructor
 * <tt>new Link(recordId)</tt>.
 *
 * <p>The Link class is immutable after construction. You have to either pass all properties
 * through constructor arguments, or use the LinkBuilder class obtained via {@link #newBuilder}.
 *
 * <p>Example using LinkBuilder:
 *
 * <pre>
 * RecordId recordId = ...;
 * Link link = Link.newBuilder().recordId(recordId).copyAll(true).remove("dev").set("foo", "bar").create();
 * </pre>
 *
 * <h2>Resolving links to RecordId's</h2>
 *
 * <p>To resolve a link to a RecordId, using the {@link #resolve(RecordId, IdGenerator)} method.
 */
public class Link {
    private RecordId masterRecordId;
    private boolean copyAll;
    private SortedMap<String, PropertyValue> variantProps;

    /**
     * A link to the master.
     */
    public Link() {
    }

    /**
     * If copyAll is true, a link to self.
     */
    public Link(boolean copyAll) {
        this.copyAll = copyAll;
    }

    /**
     * An absolute link to the specified recordId. Nothing will be copied from the context
     * when resolving this link.
     */
    public Link(RecordId recordId) {
        this.masterRecordId = recordId != null ? recordId.getMaster() : null;
        variantProps = createVariantProps(recordId);
    }

    /**
     * A relative link to the specified recordId. All variant properties will be copied
     * from the context when resolving this link, except those that would be explicitly
     * specified on the recordId.
     */
    public Link(RecordId recordId, boolean copyAll) {
        this(recordId);
        this.copyAll = copyAll;
    }

    private Link(RecordId masterRecordId, boolean copyAll, SortedMap<String, PropertyValue> props) {
        this.masterRecordId = masterRecordId;
        this.copyAll = copyAll;
        this.variantProps = props;
    }

    private static SortedMap<String, PropertyValue> createVariantProps(RecordId recordId) {
        if (recordId == null)
            return null;
        
        SortedMap<String, PropertyValue> variantProps = null;
        if (!recordId.isMaster()) {
            variantProps = new TreeMap<String, PropertyValue>();
            for (Map.Entry<String, String> entry : recordId.getVariantProperties().entrySet()) {
                variantProps.put(entry.getKey(), new PropertyValue(PropertyMode.SET, entry.getValue()));
            }
        }
        return variantProps;
    }

    /**
     * Parses a link in the syntax produced by {@link #toString()}.
     *
     * @throws IllegalArgumentException in case of syntax errors in the link.
     */
    public static Link fromString(String link, IdGenerator idGenerator) {
        ArgumentValidator.notNull(link, "link");

        if (link.equals("") || link.equals("?")) {
            return new Link();
        }

        int qpos = link.indexOf('?');

        String id;
        String argString;

        if (qpos == -1) {
            id = link;
            argString = "";
        } else {
            id = link.substring(0, qpos);
            argString = link.substring(qpos + 1);
        }

        if (id.equals(""))
            id = null;

        RecordId recordId = id != null ? idGenerator.fromString(id) : null;
        LinkBuilder builder = Link.newBuilder().recordId(recordId);

        String[] args = argString.split("&");

        for (String arg : args) {
            arg = arg.trim();
            if (arg.length() == 0)
                continue;

            if (arg.equals("*")) {
                builder.copyAll(true);
                continue;
            }

            if (arg.length() == 1)
                throw new IllegalArgumentException("Invalid link: " + link);

            int epos = arg.indexOf('=');
            if (epos == -1) {
                if (arg.startsWith("+")) {
                    builder.copy(arg.substring(1));
                } else if (arg.startsWith("-")) {
                    builder.remove(arg.substring(1));
                } else {
                    throw new IllegalArgumentException("Invalid link: " + link);
                }
            } else {
                String name = arg.substring(0, epos);
                // TODO decode special characters in the value
                String value = arg.substring(epos + 1);
                if (name.length() == 0 || value.length() == 0) {
                    throw new IllegalArgumentException("Invalid link: " + link);
                }
                builder.set(name, value);
            }
        }

        return builder.create();
    }

    public RecordId getMasterRecordId() {
        return masterRecordId;
    }

    public boolean copyAll() {
        return copyAll;
    }

    public Map<String, PropertyValue> getVariantProps() {
        return Collections.unmodifiableMap(variantProps);
    }

    /**
     * Creates a string representation of this link.
     *
     * <p>The syntax is:
     *
     * <pre>{recordId}?*&amp;arg1=val1&amp;+arg2&amp;-arg3<pre>
     *
     * <p>The recordId is optional. Arguments, if any, following after the ? symbol and are separated by &amp;
     * symbols, hence similar to URLs.
     *
     * <p>The '*' argument indicates copyAll.
     *
     * <p><tt>arg1=val1</tt> is an example of specifying an exact value for a variant property.
     *
     * <p><tt>+arg2</tt> is an explicit copy of the variant property 'arg2' from the context (does
     * not make sense when using copyAll = *)
     *
     * <p><<tt>-arg3</tt> is a removal (exclusion) of a variant property copied by using copyAll.
     *
     * <p>The arguments will always be specified in alphabetical order, ignoring the + or - symbol.
     * CopyAll (*) is always the first argument.
     */
    @Override
    public String toString() {
        if (masterRecordId == null && variantProps == null) {
            if (copyAll) {
                // link to self
                return "?*";
            } else {
                // link to my master
                return "?";
            }
        }

        StringBuilder builder = new StringBuilder();

        if (masterRecordId != null) {
            builder.append(masterRecordId.toString());
        }

        if (copyAll || variantProps != null) {
            builder.append("?");

            boolean firstArg = true;

            if (copyAll) {
                builder.append("*");
                firstArg = false;
            }

            if (variantProps != null) {
                for (Map.Entry<String, PropertyValue> entry : variantProps.entrySet()) {
                    if (firstArg) {
                        firstArg = false;
                    } else {
                        builder.append("&");
                    }

                    // TODO escaping of special characters in the value
                    switch (entry.getValue().mode) {
                        case COPY:
                            builder.append('+').append(entry.getKey());
                            break;
                        case REMOVE:
                            builder.append('-').append(entry.getKey());
                            break;
                        case SET:
                            builder.append(entry.getKey()).append('=').append(entry.getValue().value);
                            break;
                    }
                }
            }
        }

        return builder.toString();
    }

    public byte[] toBytes() {
        // TODO more efficient byte representation?
        try {
            return toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Link fromBytes(byte[] bytes, IdGenerator idGenerator) {
        try {
            String linkString = new String(bytes, "UTF-8");
            return Link.fromString(linkString, idGenerator);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A shortcut for resolve(contextRecord.getId(), idGenerator).
     */
    public RecordId resolve(Record contextRecord, IdGenerator idGenerator) {
        return resolve(contextRecord.getId(), idGenerator);
    }

    /**
     * Resolves this link to a concrete, absolute RecordId.
     *
     * @param contextRecordId usually the id of the record in which this link occurs.
     */
    public RecordId resolve(RecordId contextRecordId, IdGenerator idGenerator) {
        RecordId masterRecordId = this.masterRecordId == null ? contextRecordId.getMaster() : this.masterRecordId;

        Map<String, String> varProps = null;

        // the if statement is just an optimisation to avoid the map creation if not necessary
        if (variantProps != null ||
                (this.masterRecordId != null && !this.masterRecordId.isMaster()) ||
                (copyAll && !contextRecordId.isMaster())) {

            varProps = new HashMap<String, String>();

            // Optionally copy over the properties from the context record id
            if (copyAll) {
                for (Map.Entry<String, String> entry : contextRecordId.getVariantProperties().entrySet()) {
                    if (!varProps.containsKey(entry.getKey())) {
                        varProps.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // Process the manual specified variant properties
            if (variantProps != null) {
                evalProps(varProps, contextRecordId);
            }
        }

        if (varProps == null || varProps.isEmpty()) {
            return masterRecordId;
        } else {
            return idGenerator.newRecordId(masterRecordId, varProps);
        }
    }

    public enum PropertyMode {SET, COPY, REMOVE}

    public static class PropertyValue {
        private PropertyMode mode;
        private String value;

        private PropertyValue(PropertyMode mode, String value) {
            ArgumentValidator.notNull(mode, "mode");
            this.mode = mode;
            if (mode == PropertyMode.SET) {
                ArgumentValidator.notNull(value, "value");
                this.value = value;
            }
        }

        public PropertyMode getMode() {
            return mode;
        }

        /**
         * Value is only defined when the PropertyMode is SET.
         */
        public String getValue() {
            return value;
        }
    }

    private void evalProps(Map<String, String> resolvedProps, RecordId contextRecordId) {
        Map<String, String> contextProps = contextRecordId.getVariantProperties();

        for (Map.Entry<String, PropertyValue> entry : variantProps.entrySet()) {
            PropertyValue propValue = entry.getValue();
            switch (propValue.mode) {
                case SET:
                    resolvedProps.put(entry.getKey(), propValue.value);
                    break;
                case REMOVE:
                    resolvedProps.remove(entry.getKey());
                    break;
                case COPY:
                    String value = contextProps.get(entry.getKey());
                    if (value != null)
                        resolvedProps.put(entry.getKey(), value);
                    break;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        Link other = (Link)obj;
        ObjectUtils.safeEquals(masterRecordId, other.masterRecordId);

        if (copyAll != other.copyAll)
            return false;

        if (variantProps == null && other.variantProps == null)
            return true;

        if ((variantProps == null && other.variantProps != null) || (variantProps != null && other.variantProps == null))
            return false;

        if (variantProps.size() != other.variantProps.size())
            return false;

        for (Map.Entry<String, PropertyValue> entry : variantProps.entrySet()) {
            PropertyValue otherVal = other.variantProps.get(entry.getKey());
            if (otherVal == null)
                return false;
            PropertyValue val = entry.getValue();
            if (val.mode != otherVal.mode)
                    return false;
            if (!ObjectUtils.safeEquals(val.value, otherVal.value))
                return false;
        }

        return true;
    }

    public static LinkBuilder newBuilder() {
        return new LinkBuilder();
    }

    public static class LinkBuilder {
        private RecordId masterRecordId;
        private boolean copyAll;
        private Map<String, PropertyValue> variantProps;

        private LinkBuilder() {

        }

        /**
         * Calling this resets the state of the variant properties recorded so far.
         */
        public LinkBuilder recordId(RecordId recordId) {
            if (recordId != null) {
                this.masterRecordId = recordId.getMaster();
                this.variantProps = createVariantProps(recordId);
            } else {
                this.masterRecordId = null;
                this.variantProps = null;
            }
            return this;
        }

        public LinkBuilder copyAll(boolean copyAll) {
            this.copyAll = copyAll;
            return this;
        }

        public LinkBuilder copy(String propName) {
            ArgumentValidator.notNull(propName, "propName");
            initVarProps();
            variantProps.put(propName, new PropertyValue(PropertyMode.COPY, null));
            return this;
        }

        public LinkBuilder remove(String propName) {
            ArgumentValidator.notNull(propName, "propName");
            initVarProps();
            variantProps.put(propName, new PropertyValue(PropertyMode.REMOVE, null));
            return this;
        }

        public LinkBuilder set(String propName, String propValue) {
            ArgumentValidator.notNull(propName, "propName");
            ArgumentValidator.notNull(propValue, "propValue");
            initVarProps();
            variantProps.put(propName, new PropertyValue(PropertyMode.SET, propValue));
            return this;
        }

        public Link create() {
            if (variantProps == null) {
                return new Link(masterRecordId, copyAll);
            } else {
                return new Link(masterRecordId, copyAll, new TreeMap<String, PropertyValue>(variantProps));
            }
        }

        private void initVarProps() {
            if (variantProps == null)
                variantProps = new HashMap<String, PropertyValue>();
        }
    }
}
