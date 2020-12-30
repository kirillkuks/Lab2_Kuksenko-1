import ru.spbstu.pipeline.IExecutable;
import ru.spbstu.pipeline.IReader;
import ru.spbstu.pipeline.RC;
import ru.spbstu.pipeline.BaseGrammar;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Reader implements IReader {
    public Reader(Logger log) {
        logger = log;
    }

    @Override
    public RC execute(byte[] bytes) {
        String[] str = readConfig();
        if(str == null) {
            return RC.CODE_FAILED_TO_READ;
        }
        TreeMap<String, String> tokens = SyntacticReaderParser.parse(str);
        ConfigReaderParams cfgP = new ConfigReaderParams();
        RC rc = SemanticReaderParser.parse(tokens, cfgP);
        if(rc != RC.CODE_SUCCESS) {
            return rc;
        }

        byte[] bytes1 = new byte[cfgP.size];
        int readied = 0;
        do {
            readied = read(bytes1, cfgP.size);
            if (readied < 0) {
                return RC.CODE_SUCCESS;
            }
            if(readied != cfgP.size) {
                bytes1 = Arrays.copyOf(bytes1, readied);
            }

            rc = consumer.execute(bytes1);
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }

        } while (readied == cfgP.size);

        return RC.CODE_SUCCESS;
    }

    private int read(byte[] bytes, int size) {
        int readied  = 0;
        try {
            readied = reader.read(bytes, 0, size);
            if(readied != size && readied >= 0) {
                bytes = Arrays.copyOf(bytes, readied);
                logger.log(Level.INFO, "File is over");
                System.out.println("File is over!");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e);
        }
        return readied;
    }

    private String[] readConfig() {
        ArrayList<String> lines = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new FileReader(new File(config)))) {
            String line;
            while((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e);
            return null;
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public RC setInputStream(FileInputStream filename) {
        reader = filename;
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

    private FileInputStream reader;
    private String config;
    private IExecutable producer;
    private IExecutable consumer;
    private final Logger logger;
}

class SyntacticReaderParser {
    public static TreeMap<String, String> parse(String[] configData) {
        TreeMap<String, String> tokens = new TreeMap<>();
        ReaderGrammar grammar = new ReaderGrammar();
        for(String str : configData) {
            String key = str.substring(0, str.indexOf(grammar.delimiter()));
            key = key.substring(0, key.lastIndexOf(' '));
            String value = str.substring(str.indexOf(grammar.delimiter()));
            value = value.substring(value.indexOf(' ') + 1);
            tokens.put(key, value);
        }
        return tokens;
    }
}

class SemanticReaderParser {
    public static RC parse(TreeMap<String, String> tokens, ConfigReaderParams cfgP) {
        ReaderGrammar grammar = new ReaderGrammar();
        for(Map.Entry<String, String> entry : tokens.entrySet()) {
            if(entry.getKey().equals(grammar.token(ReaderGrammarWords.SIZE.ordinal()))) {
                cfgP.size = Integer.parseInt(entry.getValue());
            } else {
                return RC.CODE_CONFIG_SEMANTIC_ERROR;
            }
        }
        return RC.CODE_SUCCESS;
    }
}

class ConfigReaderParams {
    public int size;
}

class ReaderGrammar extends BaseGrammar {
    ReaderGrammar() {
        super(aTokens);
    }
    private static final String[] aTokens = new String[4];
    static {
        aTokens[0] = "Size";
        aTokens[1] = "Mode";
        aTokens[2] = "Encode";
        aTokens[3] = "Decode";
    }
}

enum ReaderGrammarWords {
    SIZE
}