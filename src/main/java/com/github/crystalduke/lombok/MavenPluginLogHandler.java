package com.github.crystalduke.lombok;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.apache.maven.plugin.logging.Log;

/**
 * JDK 標準のログ出力を Maven Plugin のログ出力へ転送する Handler.
 */
public class MavenPluginLogHandler extends Handler {

    @Setter
    private String prefix;
    private Map<Level, LogConsumer> consumers;
    private LogConsumer debug;

    @AllArgsConstructor
    private class LogConsumer implements Consumer<LogRecord> {

        Supplier<Boolean> enabled;
        Consumer<String> log_string;
        Consumer<Throwable> log_throwable;
        BiConsumer<String, Throwable> log_string_throwable;

        @Override
        public void accept(LogRecord record) {
            if (!enabled.get()) {
                return;
            }
            String msg = getFormatter().format(record);
            if (prefix != null) {
                msg = msg != null ? prefix + msg : prefix;
            }
            Throwable thrown = record.getThrown();
            if (thrown == null) {
                log_string.accept(msg);
            } else if (msg == null) {
                log_throwable.accept(thrown);
            } else {
                log_string_throwable.accept(msg, thrown);
            }
        }
    }

    /**
     * Maven Plugin でのログ出力先を指定してオブジェクトを構築する.
     *
     * @param log Maven Plugin のログ出力先
     */
    public MavenPluginLogHandler(Log log) {
        Formatter formatter = new Formatter() {

            @Override
            public String format(LogRecord record) {
                return formatMessage(record);
            }
        };
        setFormatter(formatter);
        consumers = new HashMap<>(3);
        consumers.put(Level.SEVERE, new LogConsumer(log::isErrorEnabled, log::error, log::error, log::error));
        consumers.put(Level.WARNING, new LogConsumer(log::isWarnEnabled, log::warn, log::warn, log::warn));
        consumers.put(Level.INFO, new LogConsumer(log::isInfoEnabled, log::info, log::info, log::info));
        debug = new LogConsumer(log::isDebugEnabled, log::debug, log::debug, log::debug);
    }

    /**
     * JUL のログ出力をコンストラクタで指定したインスタンスへ転送する. このクラスでは、JUL の {@link Level}
     * に対して以下のメソッドでログを転送する.
     * <table border="1">
     * <thead>
     * <tr><th>{@link Level}</th><th>呼出先メソッド</th></tr>
     * </thead>
     * <tbody>
     * <tr><td>{@link Level#SEVERE}</td><td>{@code error}</td></tr>
     * <tr><td>{@link Level#WARNING}</td><td>{@code warn}</td></tr>
     * <tr><td>{@link Level#INFO}</td><td>{@code info}</td></tr>
     * <tr><td>その他</td><td>{@code debug}</td></tr>
     * </tbody>
     * </table>
     *
     * @param record
     */
    @Override
    public void publish(LogRecord record) {
        consumers.getOrDefault(record.getLevel(), debug).accept(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        consumers = null;
        debug = null;
    }
}
