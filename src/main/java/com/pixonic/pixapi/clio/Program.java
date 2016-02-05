package com.pixonic.pixapi.clio;

import com.appmetr.monblank.*;
import com.appmetr.monblank.s2s.MonitoringS2SImpl;
import com.appmetr.monblank.s2s.dao.MonitoringDataAccess;
import com.appmetr.s2s.AppMetr;
import com.appmetr.s2s.events.Action;
import com.appmetr.s2s.events.Event;
import com.appmetr.s2s.persister.FileBatchPersister;
import org.hyperic.sigar.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Program {
    public static void main(String[] args) {
        ClioNode node = new ClioNode();
        node.run();

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        node.stop();
    }
}




