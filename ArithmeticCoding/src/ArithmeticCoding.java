import ru.spbstu.pipeline.IExecutable;
import ru.spbstu.pipeline.IExecutor;
import ru.spbstu.pipeline.RC;
import ru.spbstu.pipeline.BaseGrammar;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArithmeticCoding implements IExecutor {
    public ArithmeticCoding(Logger log) {
        logger = log;
    }

    @Override
    public RC execute(byte[] bytes) {
        if(cfgP.mode == CodingGrammarWords.ENCODE) {
            codingBytes = bytes;
            segments = setSegments(count(), codingBytes.length);
            double code = coding();
            RC rc = consumer.execute(doubleToByteArray(code));
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }

            rc = writeCoddingParams();
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }
        }
        if(cfgP.mode == CodingGrammarWords.DECODE) {
            double code = byteArrayToDouble(bytes);
            String[] strings = new String[strNum];
            RC rc = readCoddingParams(strings);
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }

            rc = consumer.execute(decoding(strings, code));
            if(rc != RC.CODE_SUCCESS) {
                return rc;
            }
        }
        return RC.CODE_SUCCESS;
    }

    private RC readCoddingParams(String[] strings) {
        try {
            for (int i = 0; i < strNum; ++i) {
                strings[i] = bufferedReader.readLine();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Expression: ", e.getMessage());
            return RC.CODE_FAILED_TO_READ;
        }
        return RC.CODE_SUCCESS;
    }

    private RC writeCoddingParams() {
        if(bufferedWriter != null) {
            try {
                bufferedWriter.write(Integer.toString(codingBytes.length));
                bufferedWriter.write(';');
                bufferedWriter.newLine();
                for(Segment segment : segments) {
                    bufferedWriter.write(Byte.toString(segment.symbol));
                    bufferedWriter.write(" ");
                    bufferedWriter.write(Double.toString(segment.left));
                    bufferedWriter.write(" ");
                    bufferedWriter.write(Double.toString(segment.right));
                    bufferedWriter.write(";");
                }
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Expression", e.getMessage());
                return RC.CODE_FAILED_TO_WRITE;
            }
        }
        return RC.CODE_SUCCESS;
    }

    private TreeMap<Byte, Integer> count() {
        TreeMap<Byte, Integer> probability = new TreeMap<Byte, Integer>();
        for (byte aByte : codingBytes) {
            if (!probability.containsKey(aByte)) {
                probability.put(aByte, 0);
            }
            int value = probability.get(aByte);
            probability.put(aByte, value + 1);
        }
        return probability;
    }

    private Segment[] setSegments(TreeMap<Byte, Integer> prob, int strLength) {
        Segment[] segments = new Segment[prob.size()];
        double left = 0;
        int i = 0;
        for(Map.Entry<Byte, Integer> m : prob.entrySet()) {
            segments[i] = new Segment();
            segments[i].left = left;
            left += (double)m.getValue() / strLength;
            segments[i].right = left;
            segments[i].symbol = m.getKey();
            ++i;
        }
        return segments;
    }

    private Segment edges(Segment[] segments, byte symbol) {
        for (Segment segment : segments) {
            if (segment.symbol == symbol) {
                Segment edges = new Segment();
                edges.left = segment.left;
                edges.right = segment.right;
                return edges;
            }
        }
        return null;
    }

    private double coding() {
        double left = 0, right = 1;
        for (byte b : codingBytes) {
            try {
                Segment edge = edges(segments, b);
                double newLeft = left + edge.left * (right - left);
                double newRight = left + edge.right * (right - left);

                left = newLeft;
                right = newRight;
            } catch (NullPointerException e) {
                logger.log(Level.SEVERE, "Exception: ", e);
            }
        }
        return (right + left) / 2;
    }

    private byte[] decoding(double code) {
        byte[] decodingBytes = new byte[codingBytes.length];
        for(int i = 0; i < codingBytes.length; ++i) {
            for(Segment segment : segments) {
                if(code > segment.left && code < segment.right) {
                    decodingBytes[i] = segment.symbol;
                    code = (code - segment.left) / (segment.right - segment.left);
                    break;
                }
            }
        }
        return decodingBytes;
    }

    private Segment parseSegments(String str) {
        String[] strings = str.split(" ");
        Segment segment = new Segment();
        segment.symbol = Byte.parseByte(strings[0]);
        segment.left = Double.parseDouble(strings[1]);
        segment.right = Double.parseDouble(strings[2]);
        return segment;
    }

    private byte[] decoding(String[] aString, double code) {
        int length = Integer.parseInt(aString[0].substring(0, aString[0].lastIndexOf(';')));

        String[] strings = aString[1].split(";");
        segments = new Segment[strings.length];
        for(int i = 0; i < strings.length; ++i) {
            segments[i] = parseSegments(strings[i]);
        }

        codingBytes = new byte[length];
        return decoding(code);
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
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public RC setConfig(String cfg) {
        config = cfg;
        String[] str = readConfig();
        TreeMap<String, String> tokens = SyntacticCodingParser.parse(str);
        cfgP = new ConfigCodingParams();
        RC rc = SemanticCodingParser.parse(tokens, cfgP);
        if(rc != RC.CODE_SUCCESS) {
            return rc;
        }
        if(cfgP.CodingParams != null) {
            if(cfgP.mode == CodingGrammarWords.ENCODE) {
                try {
                    bufferedWriter = new BufferedWriter(new FileWriter(new File(cfgP.CodingParams)));
                    bufferedWriter.write(Integer.toString(strNumForOutput) + '\n');
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Expression: ", e.getMessage());
                    return RC.CODE_INVALID_ARGUMENT;
                }
            }
            if(cfgP.mode == CodingGrammarWords.DECODE) {
                try {
                    bufferedReader = new BufferedReader(new FileReader(new File(cfgP.CodingParams)));
                    String line = bufferedReader.readLine();
                    strNum = Integer.parseInt(line);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Expression: ", e.getMessage());
                    return RC.CODE_INVALID_ARGUMENT;
                }
            }
        }
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

    private byte[] doubleToByteArray(double d) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeDouble(d);
            dos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Expression: ", e);
        }
        return null;
    }

    private double byteArrayToDouble(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.put(bytes);
        buffer.flip();

        return buffer.getDouble();
    }

    private ConfigCodingParams cfgP;
    private String config;
    private IExecutable producer;
    private IExecutable consumer;
    private final Logger logger;

    private byte[] codingBytes;
    private Segment[] segments;
    private BufferedWriter bufferedWriter;
    private BufferedReader bufferedReader;
    private final int strNumForOutput = 2;
    private int strNum;

}

class Segment {
    double left;
    double right;
    byte symbol;
}

class SyntacticCodingParser {
    public static TreeMap<String, String> parse(String[] configData) {
        TreeMap<String, String> tokens = new TreeMap<>();
        CodingGrammar grammar = new CodingGrammar();
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

class SemanticCodingParser {
    public static RC parse(TreeMap<String, String> tokens, ConfigCodingParams cfgP) {
        CodingGrammar grammar = new CodingGrammar();

        for(Map.Entry<String, String> entry : tokens.entrySet()) {
            if(entry.getKey().equals(grammar.token(CodingGrammarWords.MODE.ordinal()))) {
                if(entry.getValue().equals(grammar.token(CodingGrammarWords.ENCODE.ordinal()))) {
                    cfgP.mode = CodingGrammarWords.ENCODE;
                } else if(entry.getValue().equals(grammar.token(CodingGrammarWords.DECODE.ordinal()))) {
                    cfgP.mode = CodingGrammarWords.DECODE;
                }
            } else if (entry.getKey().equals(grammar.token(CodingGrammarWords.CODING_PARAMS.ordinal()))) {
                cfgP.CodingParams = entry.getValue();
            } else {
                return RC.CODE_CONFIG_SEMANTIC_ERROR;
            }
        }

        return RC.CODE_SUCCESS;
    }
}

class ConfigCodingParams {
    CodingGrammarWords mode;
    String CodingParams;
}

class CodingGrammar extends BaseGrammar {
    CodingGrammar() {
        super(aTokens);
    }
    private static final String[] aTokens = new String[4];
    static {
        aTokens[0] = "Mode";
        aTokens[1] = "Encode";
        aTokens[2] = "Decode";
        aTokens[3] = "CodingParams";
    }
}

enum CodingGrammarWords {
    MODE,
    ENCODE,
    DECODE,
    CODING_PARAMS
}
