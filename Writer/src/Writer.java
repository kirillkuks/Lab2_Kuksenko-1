import ru.spbstu.pipeline.IExecutable;
import ru.spbstu.pipeline.IWriter;
import ru.spbstu.pipeline.RC;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Writer implements IWriter {
    public Writer(Logger log) {
        logger = log;
    }

    @Override
    public RC execute(byte[] bytes) {
        try {
            writer.write(bytes);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Expression: ", e);
            return RC.CODE_FAILED_TO_WRITE;
        }
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setOutputStream(FileOutputStream filename) {
        writer = filename;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConfig(String cfg) {
        config = cfg;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IExecutable prod) {
        producer = prod;
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IExecutable cons) {
        consumer = cons;
        return RC.CODE_SUCCESS;
    }

    private FileOutputStream writer;
    private String config;
    private IExecutable producer;
    private IExecutable consumer;
    private final Logger logger;
}
