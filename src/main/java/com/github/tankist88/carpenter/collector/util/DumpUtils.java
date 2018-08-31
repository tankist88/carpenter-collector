package com.github.tankist88.carpenter.collector.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static com.github.tankist88.carpenter.core.property.AbstractGenerationProperties.OBJ_FILE_EXTENSION;
import static com.github.tankist88.carpenter.core.property.GenerationPropertiesFactory.loadProps;
import static com.github.tankist88.object2source.util.GenerationUtil.getPackage;

public class DumpUtils {
    private static final Logger logger = LoggerFactory.getLogger(DumpUtils.class);

    public static synchronized void saveObjectDump(
            Serializable object,
            String methodKey,
            String className,
            String upLevelKey
    ) {
        DataOutputStream dos = null;
        try {
            String key = methodKey + "_" + upLevelKey;
            String keyHash = DigestUtils.md5Hex(key);
            String packageFileStruct =
                    loadProps().getObjectDumpDir() +
                            "/" +
                            getPackage(className).replaceAll("\\.", "/");
            FileUtils.forceMkdir(new File(packageFileStruct));
            File file = new File(packageFileStruct, keyHash + "." + OBJ_FILE_EXTENSION);
            dos = new DataOutputStream(new FileOutputStream(file));
            byte[] bytes = SerializationUtils.serialize(object);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        } catch (IOException iex) {
            String errorMsg = "Can't save object dump";
            logError(errorMsg, iex);
            throw new IllegalStateException(errorMsg, iex);
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException iex) {
                    logError("Can't close stream", iex);
                }
            }
        }
    }

    private static void logError(String text, IOException iex) {
        String errorMsg = text + ". " + iex.getMessage();
        System.out.println(errorMsg);
        System.err.println(errorMsg);
        logger.error(errorMsg);
    }
}
