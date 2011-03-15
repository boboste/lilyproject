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
package org.lilyproject.repository.impl.primitivevaluetype;

import org.lilyproject.bytes.api.DataInput;
import org.lilyproject.bytes.api.DataOutput;
import org.lilyproject.hbaseext.ContainsValueComparator;
import org.lilyproject.repository.api.Blob;
import org.lilyproject.repository.api.PrimitiveValueType;

public class BlobValueType implements PrimitiveValueType {
    private final String NAME = "BLOB";

    public String getName() {
        return NAME;
    }

    /**
     * See write for the byte format.
     */
    public Object read(DataInput dataInput) {
        int keyLength = dataInput.readVInt();
        byte[] key = null;
        if (keyLength > 0) {
            key = dataInput.readBytes(keyLength);
        }
        String mediaType = dataInput.readUTF();
        Long size = dataInput.readLong();
        if (size == -1) {
            size = null;
        }
        String filename = dataInput.readUTF();
        return new Blob(key, mediaType, size, filename);
    }

    /**
     * Format of the bytes written :
     * - Length of the blob value : int of 4 bytes
     * - Blob Value
     * - Blob Media Type : UTF (which starts with an int of 4 bytes indicating its length)
     * - Blob size : long of 8 bytes
     * - Blob name : UTF (which starts with an int of 4 bytes indicating its length)
     * 
     * <p> IMPORTANT: Any changes on this format has an impact on the {@link ContainsValueComparator}
     */
    public void write(Object value, DataOutput dataOutput) {
        Blob blob = (Blob)value;
        byte[] key = blob.getValue();
        if (key == null) {
            dataOutput.writeVInt(0);
        } else {
            dataOutput.writeVInt(key.length);
            dataOutput.writeBytes(key);
        }
        dataOutput.writeUTF(blob.getMediaType());
        Long size = blob.getSize();
        if (size == null) {
            size = Long.valueOf(-1);
        }
        dataOutput.writeLong(size);
        dataOutput.writeUTF(blob.getName());
    }

    public Class getType() {
        return Blob.class;
    }

}
