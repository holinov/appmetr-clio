package com.pixonic.pixapi.clio;

import com.appmetr.monblank.MonblankConst;
import com.appmetr.monblank.MonitorProperties;
import com.appmetr.monblank.StopWatch;
import com.appmetr.monblank.s2s.MonitoringS2SImpl;
import com.appmetr.monblank.s2s.dao.MonitoringDataAccess;
import com.appmetr.s2s.AppMetr;
import com.appmetr.s2s.persister.FileBatchPersister;
import org.hyperic.sigar.*;
import org.hyperic.sigar.ptql.StringPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ClioNode {
    private final Logger logger = LoggerFactory.getLogger(ClioNode.class);

    private Sigar sigar;
    private AppMetr appMetr;
    private MonitoringS2SImpl monitoring;
    private MonitoringDataAccess monitoringDataAccess;
    private ScheduledExecutorService threadPool;

    public void run() {
        logger.info("java.library.path = "+System.getProperty("java.library.path"));
        sigar=new Sigar();
        logger.info("Started sigar "+sigar);
        try {
            logger.info("SIGAR FQDN "+sigar.getFQDN());
        } catch (SigarException e) {
            logger.error("SIGAR Error",e);
        }
        appMetr = new AppMetr(ClioParams.DEPLOY_TOKEN, "http://appmetr.com/api",new FileBatchPersister(ClioParams.BATCH_FILES));
        monitoring = new MonitoringS2SImpl();
        monitoringDataAccess = new MonitoringDataAccess(monitoring, appMetr);

        threadPool = Executors.newScheduledThreadPool(ClioParams.NODE_THREAD_POOL_SIZE);
        threadPool.scheduleAtFixedRate(() -> {
            try {
                collectData();
            } catch (SigarException e) {
                logger.error("Error collecting SIGAR data", e);
            }
        }, 0, ClioParams.DELAY_VALUE, ClioParams.DELAY_TYPE);
    }

    public void stop() {
        if (!threadPool.isShutdown())
            threadPool.shutdown();
        monitoringDataAccess.execute();
        monitoringDataAccess.stop();
        appMetr.stop();
    }

    private Map<String,String> getNodeFeatures(){
        String hostName = "localhost";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            logger.warn("could not get local hostname", e);

        }
        final OperatingSystem operatingSystem = OperatingSystem.getInstance();


        return MonitorProperties.create()
                .add("hostname", hostName)
                .add("arch",operatingSystem.getArch())
                .asMap();
    }

    private void collectData()
            throws SigarException {
        final StopWatch stopWatch = monitoring.start(ClioParams.MONITORING_GROUP, "collectData() execution time");
        logger.info("Collecting monitoring data");
        final Map<String, String> map = getNodeFeatures();

        //CPU metrics
        final Cpu cpu = sigar.getCpu();
        monitorVal("CPU", "CPU idle", ClioParams.PRESENT_UNITS, cpu.getIdle(), map);
        monitorVal("CPU", "CPU sys", ClioParams.PRESENT_UNITS, cpu.getSys(), map);
        monitorVal("CPU", "CPU total", ClioParams.PRESENT_UNITS, cpu.getTotal(), map);
        monitorVal("CPU", "CPU user", ClioParams.PRESENT_UNITS, cpu.getUser(), map);
        monitorVal("CPU", "CPU wait", ClioParams.PRESENT_UNITS, cpu.getWait(), map);

        //Memory metrics
        final Mem mem = sigar.getMem();
        monitorVal("Memory", "Mem total", MonblankConst.BYTES, mem.getTotal(), map);
        monitorVal("Memory", "Mem used", MonblankConst.BYTES, mem.getUsed(), map);
        monitorVal("Memory", "Mem used", ClioParams.PRESENT_UNITS, mem.getUsedPercent(), map);
        monitorVal("Memory", "Mem free", MonblankConst.BYTES, mem.getFree(), map);
        monitorVal("Memory", "Mem free", ClioParams.PRESENT_UNITS, mem.getFreePercent(), map);

        //Disk usage stats
        /*for (FileSystem fileSystem : sigar.getFileSystemList()) {
            final DiskUsage diskUsage = sigar.getDiskUsage(fileSystem.getDevName());
            final Map<String,String> diskMap = new HashMap<>(map);
            diskMap.put("Disk name",fileSystem.getDevName());
            diskMap.put("Disk dir",fileSystem.getDirName());
            diskMap.put("Type",fileSystem.getTypeName());

            monitoring.add(ClioParams.MONITORING_GROUP, "Disk queue", MonblankConst.COUNT, diskUsage.getQueue(), diskMap);
            monitoring.add(ClioParams.MONITORING_GROUP, "Disk read bytes", MonblankConst.BYTES, diskUsage.getReadBytes(), diskMap);
            monitoring.add(ClioParams.MONITORING_GROUP, "Disk reads count", MonblankConst.COUNT, diskUsage.getReads(), diskMap);
            monitoring.add(ClioParams.MONITORING_GROUP, "Disk read bytes", MonblankConst.BYTES, diskUsage.getWriteBytes(), diskMap);
            monitoring.add(ClioParams.MONITORING_GROUP, "Disk reads count", MonblankConst.COUNT, diskUsage.getWrites(), diskMap);
            monitoring.add(ClioParams.MONITORING_GROUP, "Disk read bytes", MonblankConst.MS, diskUsage.getServiceTime(), diskMap);
        }*/

        //Network stats
        for (String netiface : sigar.getNetInterfaceList()) {
            final NetInterfaceStat netInterfaceStat = sigar.getNetInterfaceStat(netiface);
            final Map<String,String> netMap = new HashMap<>(map);
            logger.debug("Net stats for "+netiface);
            netMap.put("iface name",netiface);

            monitorVal("Network", "RX bytes", MonblankConst.BYTES, netInterfaceStat.getRxBytes(), netMap);
            monitorVal("Network", "TX bytes", MonblankConst.BYTES, netInterfaceStat.getTxBytes(), netMap);

            monitorVal("Network", "RX errors", MonblankConst.COUNT, netInterfaceStat.getRxErrors(), netMap);
            monitorVal("Network", "TX errors", MonblankConst.COUNT, netInterfaceStat.getTxDropped(), netMap);

            monitorVal("Network", "RX dropped", MonblankConst.COUNT, netInterfaceStat.getRxDropped(), netMap);
            monitorVal("Network", "TX dropped", MonblankConst.COUNT, netInterfaceStat.getTxDropped(), netMap);

            monitorVal("Network", "RX errors", MonblankConst.COUNT, netInterfaceStat.getRxErrors(), netMap);
            monitorVal("Network", "TX errors", MonblankConst.COUNT, netInterfaceStat.getTxErrors(), netMap);
        }
        stopWatch.stop();
        logger.info("Finished collecting monitoring data");
    }

    private void monitorVal(String group,String name,String units,double value,Map<String,String> map){
        final String key = getMonitorName(group);
        if (value!=0)
            logger.trace(String.format("Monitor. Group: %s name: %s units: %s val:%s map: %s", key, name, units, value, map));
        monitoring.add(key, name, units, value, map);
    }

    private String getMonitorName(String group) {return String.format("%s.%s", ClioParams.MONITORING_GROUP, group);}
}
