package io.metersphere.jmeter.reporters;

import com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup;
import kg.apc.jmeter.threads.UltimateThreadGroup;
import net.sf.json.JSONArray;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.reporters.AbstractListenerElement;
import org.apache.jmeter.samplers.Remoteable;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.ThreadGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadGroupAutoStop
        extends AbstractListenerElement
        implements SampleListener, Serializable,
        ThreadListener, TestStateListener, Remoteable, NoThreadClone {

    private static final Logger log = LoggerFactory.getLogger(ThreadGroupAutoStop.class);
    private final static String DELAY_SECONDS = "delay_seconds";
    private int delaySeconds = 0;
    private final ConcurrentHashMap<AbstractThreadGroup, AtomicBoolean> once = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> threadGroupStartTime = new ConcurrentHashMap<>();


    public ThreadGroupAutoStop() {
        super();
    }

    @Override
    public void sampleOccurred(SampleEvent se) {
        AbstractThreadGroup threadGroup = JMeterContextService.getContext().getThreadGroup();
        if (threadGroup instanceof ThreadGroup && !((ThreadGroup) threadGroup).getScheduler()) {
            return;
        }
        once.putIfAbsent(threadGroup, new AtomicBoolean(false));

        if (threadGroup instanceof ThreadGroup) {
            processThreadGroup(threadGroup);
        }
        if (threadGroup instanceof ConcurrencyThreadGroup) {
            processConcurrencyThreadGroup(threadGroup);
        }
        if (threadGroup instanceof UltimateThreadGroup) {
            processUltimateThreadGroup(threadGroup);
        }
    }

    private void processUltimateThreadGroup(AbstractThreadGroup threadGroup) {
        JMeterProperty data = ((UltimateThreadGroup) threadGroup).getData();
        JSONArray jsonArray = JSONArray.fromObject(data.getObjectValue().toString());
        long sum = 0;
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONArray jsonArray1 = jsonArray.getJSONArray(i);
            long temp = Arrays.stream(jsonArray1.toArray()).skip(1).mapToLong(item -> Long.parseLong(item.toString())).sum();
            if (temp >= sum) {
                sum = temp;
            }
        }
        long startTime = threadGroupStartTime.get(threadGroup.getName());
        long offset = System.currentTimeMillis() / 1000 - (startTime + sum);
        if (offset >= 0 && sum > 0) {
            if (!once.get(threadGroup).getAndSet(true)) {
                new Timer(true).schedule(new TimerTask() {
                    public void run() {
                        threadGroup.stop();
                        log.info("Expected duration reached, shutdown the UltimateThreadGroup");
                        this.cancel();
                    }
                }, delaySeconds * 1000L);
            }
        }
    }

    private void processConcurrencyThreadGroup(AbstractThreadGroup threadGroup) {
        long startTime = threadGroupStartTime.get(threadGroup.getName() + "-ThreadStarter");
        long holdSeconds = ((ConcurrencyThreadGroup) threadGroup).getHoldSeconds();
        long rampUpSeconds = ((ConcurrencyThreadGroup) threadGroup).getRampUpSeconds();
        long offset = System.currentTimeMillis() / 1000 - (startTime + holdSeconds + rampUpSeconds);

        if (offset >= 0 && startTime > 0) {
            if (!once.get(threadGroup).getAndSet(true)) {
                new Timer(true).schedule(new TimerTask() {
                    public void run() {
                        threadGroup.stop();
                        log.info("Expected duration reached, shutdown the ConcurrencyThreadGroup");
                        this.cancel();
                    }
                }, delaySeconds * 1000L);
            }
        }
    }

    private void processThreadGroup(AbstractThreadGroup threadGroup) {
        long startTime = threadGroupStartTime.get(threadGroup.getName());
        long duration = ((ThreadGroup) threadGroup).getDuration();
        long offset = System.currentTimeMillis() / 1000 - (startTime + duration);

        if (offset >= 0 && startTime > 0) {
            if (!once.get(threadGroup).getAndSet(true)) {
                new Timer(true).schedule(new TimerTask() {
                    public void run() {
                        try {
                            Field allThreads = FieldUtils.getDeclaredField(ThreadGroup.class, "allThreads", true);
                            ConcurrentHashMap<JMeterThread, Thread> o = (ConcurrentHashMap<JMeterThread, Thread>) allThreads.get(threadGroup);
                            o.keySet().forEach(JMeterThread::interrupt);
                            threadGroup.stop();
                        } catch (IllegalAccessException e) {
                            log.error(e.getMessage(), e);
                        }
                        log.info("Expected duration reached, shutdown the ThreadGroup");
                        this.cancel();
                    }
                }, delaySeconds * 1000L);
            }
        }
    }

    @Override
    public void sampleStarted(SampleEvent se) {
    }

    @Override
    public void sampleStopped(SampleEvent se) {
    }

    void setDelaySecs(String text) {
        setProperty(DELAY_SECONDS, text);
    }

    String getDelaySecs() {
        return getPropertyAsString(DELAY_SECONDS);
    }


    private int getDelaySecsAsInt() {
        int res = 0;
        try {
            res = Integer.valueOf(getDelaySecs());
        } catch (NumberFormatException e) {
            log.error("Wrong delay seconds: " + getDelaySecs(), e);
            setDelaySecs("30");
        }
        return res > 0 ? res : 30;
    }

    @Override
    public void threadStarted() {
        Thread thread = Thread.currentThread();
        String threadName = thread.getName();

        int x = threadName.lastIndexOf(" ");
        String threadGroupName = threadName.substring(0, x);
        threadGroupStartTime.putIfAbsent(threadGroupName, System.currentTimeMillis() / 1000);
    }

    @Override
    public void threadFinished() {
    }

    @Override
    public void testStarted() {
        delaySeconds = getDelaySecsAsInt();
    }

    @Override
    public void testStarted(String s) {

    }

    @Override
    public void testEnded() {

    }

    @Override
    public void testEnded(String s) {

    }
}
