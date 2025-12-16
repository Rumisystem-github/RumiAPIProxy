native-image \
-jar ./target/rumi_api_proxy-1.0-jar-with-dependencies.jar \
./target/rumi_api_proxy \
-Dfile.encoding=UTF-8 \
--initialize-at-build-time=org.slf4j.impl.StaticLoggerBinder,org.slf4j.LoggerFactory,ch.qos.logback.classic.Logger,ch.qos.logback.core.spi.AppenderAttachableImpl,ch.qos.logback.core.status.StatusBase,ch.qos.logback.classic.Level,ch.qos.logback.core.status.InfoStatus,ch.qos.logback.classic.PatternLayout,ch.qos.logback.core.CoreConstants
