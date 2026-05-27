package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface PacketEncode {
    void encode(ByteArrayOutputStream out) throws IOException;
}

