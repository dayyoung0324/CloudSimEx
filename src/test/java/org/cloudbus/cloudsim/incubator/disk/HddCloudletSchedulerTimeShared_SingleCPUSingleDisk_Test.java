package org.cloudbus.cloudsim.incubator.disk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.incubator.util.CustomLog;
import org.cloudbus.cloudsim.incubator.util.Id;
import org.cloudbus.cloudsim.incubator.util.helpers.TestUtil;
import org.cloudbus.cloudsim.incubator.web.IGenerator;
import org.cloudbus.cloudsim.incubator.web.ILoadBalancer;
import org.cloudbus.cloudsim.incubator.web.IterableGenerator;
import org.cloudbus.cloudsim.incubator.web.SimpleWebLoadBalancer;
import org.cloudbus.cloudsim.incubator.web.WebBroker;
import org.cloudbus.cloudsim.incubator.web.WebCloudlet;
import org.cloudbus.cloudsim.incubator.web.WebDataCenter;
import org.cloudbus.cloudsim.incubator.web.WebSession;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author nikolay.grozev
 * 
 */
public class HddCloudletSchedulerTimeShared_SingleCPUSingleDisk_Test {

    private static final int ITEM_SIZE = 5;
    private static final double DELTA = 0.01;

    protected WebDataCenter datacenter;
    protected WebBroker broker;
    protected HddVm vm1;
    protected HddVm vm2;
    protected static DataItem dataItem1 = new DataItem(ITEM_SIZE);

    private static final int HOST_MIPS = 1000;
    private static final int HOST_MIOPS = 100;
    private static final int HOST_RAM = 2048; // host memory (MB)
    private static final long HOST_STORAGE = 1000000; // host storage
    private static final int HOST_BW = 10000;

    private static final int VM_MIPS = 250;
    private static final long VM_SIZE = 10000;
    private static final int VM_RAM = 512;
    private static final long VM_BW = 1000;

    @Before
    public void setUp() throws Exception {
	Properties logProps = new Properties();
	// logProps.put(CustomLog.LOG_LEVEL_PROP_KEY, Level.FINEST.getName());
	// logProps.put("ShutStandardLogger", "true");
	CustomLog.configLogger(logProps);

	int numBrokers = 1;
	boolean trace_flag = false;

	CloudSim.init(numBrokers, Calendar.getInstance(), trace_flag);

	datacenter = createDatacenterWithSingleHostAndSingleDisk("TestDatacenter");

	// Create Broker
	broker = new WebBroker("Broker", 5, 10000);

	// Create virtual machines
	List<Vm> vmlist = new ArrayList<Vm>();

	int pesNumber = 1; // number of cpus
	String vmm = "Xen"; // VMM name

	// create two VMs
	vm1 = new HddVm(broker.getId(), VM_MIPS, HOST_MIOPS, pesNumber,
		VM_RAM, VM_BW, VM_SIZE, vmm, new HddCloudletSchedulerTimeShared());
	vm2 = new HddVm(broker.getId(), VM_MIPS, HOST_MIOPS, pesNumber,
		VM_RAM, VM_BW, VM_SIZE, vmm, new HddCloudletSchedulerTimeShared());

	// add the VMs to the vmList
	vmlist.add(vm1);

	// submit vm list to the broker
	broker.submitVmList(vmlist);
    }

    @Test
    public void testOneJobMoreIOThanCPU() {
	double timesMips = 5;
	double timesIOMIPS = 12;
	testSingleCloudlet(timesMips, timesIOMIPS);
    }

    @Test
    public void testOneJobMoreCPUThanIO() {
	double timesMips = 13;
	double timesIOMIPS = 12;
	testSingleCloudlet(timesMips, timesIOMIPS);
    }

    @Test
    public void testOneJobEqualCPUAndIO() {
	double timesMips = 13;
	double timesIOMIPS = timesMips;
	testSingleCloudlet(timesMips, timesIOMIPS);
    }

    @Test
    public void testTwoJobs() {
	double job1TimesMips = 2;
	double job1TimesIOMIPS = 1;

	double job2TimesMips = 1;
	double job2TimesIOMIPS = 2;

	WebCloudlet cloudlet1 = new WebCloudlet(0, (int) (VM_MIPS * job1TimesMips),
		(int) (HOST_MIOPS * job1TimesIOMIPS), 5, broker.getId(), dataItem1);
	WebCloudlet cloudlet2 = new WebCloudlet(0, (int) (VM_MIPS * job2TimesMips),
		(int) (HOST_MIOPS * job2TimesIOMIPS), 5, broker.getId(), dataItem1);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(2, resultList.size());

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.SUCCESS);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.SUCCESS);

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();
	assertEquals(3, cloudletExecTime1, DELTA);
	assertEquals(3, cloudletExecTime2, DELTA);
    }

    @Test
    public void testTwoJobsNoIO() {
	double job1TimesMips = 2;
	double job1TimesIOMIPS = 0;

	double job2TimesMips = 1;
	double job2TimesIOMIPS = 0;

	WebCloudlet cloudlet1 = new WebCloudlet(0, (int) (VM_MIPS * job1TimesMips),
		(int) (HOST_MIOPS * job1TimesIOMIPS), 5, broker.getId(), null);
	WebCloudlet cloudlet2 = new WebCloudlet(0, (int) (VM_MIPS * job2TimesMips),
		(int) (HOST_MIOPS * job2TimesIOMIPS), 5, broker.getId(), null);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(2, resultList.size());

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.SUCCESS);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.SUCCESS);

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();
	assertEquals(3, cloudletExecTime1, DELTA);
	assertEquals(2, cloudletExecTime2, DELTA);
    }

    @Test
    public void testTwoJobsNoCPU() {
	double job1TimesMips = 0;
	double job1TimesIOMIPS = 1;

	double job2TimesMips = 0;
	double job2TimesIOMIPS = 2;

	WebCloudlet cloudlet1 = new WebCloudlet(0, (int) (VM_MIPS * job1TimesMips),
		(int) (HOST_MIOPS * job1TimesIOMIPS), 5, broker.getId(), dataItem1);
	WebCloudlet cloudlet2 = new WebCloudlet(0, (int) (VM_MIPS * job2TimesMips),
		(int) (HOST_MIOPS * job2TimesIOMIPS), 5, broker.getId(), dataItem1);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(2, resultList.size());

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.SUCCESS);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.SUCCESS);

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();

	// We need a bigger delta, since cloulets always have at least 1
	// instruction - it is never 0 even if we set it to 0 and that messes up
	// the forecasted execution time.
	double biggerDelta = DELTA * 10;
	assertEquals(2, cloudletExecTime1, biggerDelta);
	assertEquals(3, cloudletExecTime2, biggerDelta);
    }

    @Test
    public void testTwoJobsLowCPUHighIO() {
	double job1TimesMips = 00.1;
	double job1TimesIOMIPS = 5;

	double job2TimesMips = 0.1;
	double job2TimesIOMIPS = 10;

	WebCloudlet cloudlet1 = new WebCloudlet(0, (int) (VM_MIPS * job1TimesMips),
		(int) (HOST_MIOPS * job1TimesIOMIPS), 5, broker.getId(), dataItem1);
	WebCloudlet cloudlet2 = new WebCloudlet(0, (int) (VM_MIPS * job2TimesMips),
		(int) (HOST_MIOPS * job2TimesIOMIPS), 5, broker.getId(), dataItem1);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.SUCCESS);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.SUCCESS);

	assertEquals(2, resultList.size());

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();

	assertEquals(10, cloudletExecTime1, DELTA);
	assertEquals(15, cloudletExecTime2, DELTA);
    }

    @Test
    public void testTwoJobsLowIOHighCPU() {
	double job1TimesMips = 10;
	double job1TimesIOMIPS = 0.01;

	double job2TimesMips = 5;
	double job2TimesIOMIPS = 0.1;

	WebCloudlet cloudlet1 = new WebCloudlet(0, (int) (VM_MIPS * job1TimesMips),
		(int) (HOST_MIOPS * job1TimesIOMIPS), 5, broker.getId(), dataItem1);
	WebCloudlet cloudlet2 = new WebCloudlet(0, (int) (VM_MIPS * job2TimesMips),
		(int) (HOST_MIOPS * job2TimesIOMIPS), 5, broker.getId(), dataItem1);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.SUCCESS);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.SUCCESS);

	assertEquals(2, resultList.size());

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();

	assertEquals(15, cloudletExecTime1, DELTA);
	assertEquals(10, cloudletExecTime2, DELTA);
    }

    @Test
    public void testOutOfMemoryTwoCloudlets() {
	WebCloudlet cloudlet1 = new WebCloudlet(0, 100,
		100, VM_RAM / 2 + 1, broker.getId(), null);
	WebCloudlet cloudlet2 = new WebCloudlet(0, 100,
		100, VM_RAM / 2 + 1, broker.getId(), null);
	broker.submitCloudletList(Arrays.asList(cloudlet1, cloudlet2));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(2, resultList.size());

	double cloudletExecTime1 = cloudlet1.getFinishTime() - cloudlet1.getExecStartTime();
	double cloudletExecTime2 = cloudlet2.getFinishTime() - cloudlet2.getExecStartTime();

	assertEquals(cloudlet1.getCloudletStatus(), Cloudlet.FAILED);
	assertEquals(cloudlet2.getCloudletStatus(), Cloudlet.FAILED);

	assertTrue(cloudletExecTime1 <= 0);
	assertTrue(cloudletExecTime2 <= 0);
    }

    @Test
    public void testMultipleJobs() {
	Random rand = new Random(TestUtil.SEED);
	int jobCount = 100;
	List<WebCloudlet> jobs = new ArrayList<>();

	int minMipsFactor = 5;
	int maxMipsFactor = 10;
	int minMiopsFactor = 5;
	int maxMiopsFactor = 10;

	for (int i = 0; i < jobCount; i++) {
	    double jobTimesMips = nextRandBetween(rand, minMipsFactor, maxMipsFactor);
	    double jobTimesIOMIPS = nextRandBetween(rand, minMiopsFactor, maxMiopsFactor);
	    jobs.add(new WebCloudlet(0, (int) (VM_MIPS * jobTimesMips),
		    (int) (HOST_MIOPS * jobTimesIOMIPS), 5, broker.getId(), dataItem1));
	}

	broker.submitCloudletList(jobs);

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(jobs.size(), resultList.size());

	for (WebCloudlet job : jobs) {
	    assertEquals(job.getCloudletStatus(), Cloudlet.SUCCESS);

	    double cloudletExecTime = job.getFinishTime() - job.getExecStartTime();
	    assertTrue(cloudletExecTime > minMipsFactor);
	    assertTrue(cloudletExecTime > minMiopsFactor);
	    assertTrue(cloudletExecTime < maxMipsFactor * jobCount);
	    assertTrue(cloudletExecTime < maxMiopsFactor * jobCount);
	}
    }

    @Test
    public void testWebSessions() {
	broker.submitVmList(Arrays.asList(vm2));

	double factor1 = 0.5;
	double factor2 = 0.25;
	WebCloudlet asCl1 = new WebCloudlet(0, (int) (VM_MIPS * factor1),
		0, 10, broker.getId(), null);
	WebCloudlet asCl2 = new WebCloudlet(0, (int) (VM_MIPS * factor2),
		0, 10, broker.getId(), null);

	WebCloudlet dbCl1 = new WebCloudlet(0, (int) (VM_MIPS * factor2),
		(int) (HOST_MIOPS * factor2), 10, broker.getId(), dataItem1);
	WebCloudlet dbCl2 = new WebCloudlet(0, (int) (VM_MIPS * factor1),
		(int) (HOST_MIOPS * factor1), 10, broker.getId(), dataItem1);

	IGenerator<WebCloudlet> generatorAS = new IterableGenerator<>(Arrays.asList(asCl1, asCl2));
	IGenerator<WebCloudlet> generatorDB = new IterableGenerator<>(Arrays.asList(dbCl1, dbCl2));

	ILoadBalancer balancer = new SimpleWebLoadBalancer(Arrays.asList(vm1), vm2);
	broker.addLoadBalancer(balancer);

	WebSession session = new WebSession(generatorAS, generatorDB, broker.getId(), 2, -1);
	session.setAppVmId(vm1.getId());
	session.setDbVmId(vm2.getId());

	broker.submitSessions(Arrays.asList(session), balancer.getId());

	CloudSim.startSimulation();
	// List<WebCloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(asCl1.getExecStartTime(), dbCl1.getExecStartTime(), DELTA);
	assertEquals(asCl2.getExecStartTime(), dbCl2.getExecStartTime(), DELTA);

	assertTrue(asCl1.isFinished());
	assertTrue(asCl2.isFinished());
	assertTrue(dbCl1.isFinished());
	assertTrue(dbCl2.isFinished());

	double asCl1Len = asCl1.getFinishTime() - asCl1.getExecStartTime();
	double asCl2Len = asCl2.getFinishTime() - asCl2.getExecStartTime();
	double dbCl1Len = dbCl1.getFinishTime() - dbCl1.getExecStartTime();
	double dbCl2Len = dbCl2.getFinishTime() - dbCl2.getExecStartTime();

	assertTrue(asCl1Len > dbCl1Len);
	assertTrue(asCl2Len < dbCl2Len);
    }

    private double nextRandBetween(final Random rand, final double start, final double end) {
	return rand.nextDouble() * (end - start) + start;
    }

    private void testSingleCloudlet(final double timesMips, final double timesIOMIPS) {
	WebCloudlet cloudlet = new WebCloudlet(0, (int) (VM_MIPS * timesMips), (int) (HOST_MIOPS * timesIOMIPS), 5,
		broker.getId(), dataItem1);
	broker.submitCloudletList(Arrays.asList(cloudlet));

	CloudSim.startSimulation();
	List<Cloudlet> resultList = broker.getCloudletReceivedList();
	CloudSim.stopSimulation();

	assertEquals(1, resultList.size());
	Cloudlet resCloudlet = resultList.get(0);

	assertEquals(cloudlet.getCloudletStatus(), Cloudlet.SUCCESS);

	double cloudletExecTime = resCloudlet.getFinishTime() - resCloudlet.getExecStartTime();
	assertEquals(Math.max(timesMips, timesIOMIPS), cloudletExecTime, DELTA);
    }

    private static WebDataCenter createDatacenterWithSingleHostAndSingleDisk(final String name) {
	List<Host> hostList = new ArrayList<Host>();

	List<Pe> peList = new ArrayList<>();
	List<HddPe> hddList = new ArrayList<>();

	peList.add(new Pe(Id.pollId(Pe.class), new PeProvisionerSimple(HOST_MIPS)));
	hddList.add(new HddPe(new PeProvisionerSimple(HOST_MIOPS), dataItem1));

	hostList.add(new HddHost(new RamProvisionerSimple(HOST_RAM),
		new BwProvisionerSimple(HOST_BW), HOST_STORAGE, peList, hddList,
		new VmSchedulerTimeShared(peList), new VmDiskScheduler(hddList)));

	String arch = "x86";
	String os = "Linux";
	String vmm = "Xen";
	double time_zone = 10.0;
	double cost = 3.0;
	double costPerMem = 0.05;
	double costPerStorage = 0.001;
	double costPerBw = 0.0;
	LinkedList<Storage> storageList = new LinkedList<Storage>();

	DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
		arch, os, vmm, hostList, time_zone, cost, costPerMem,
		costPerStorage, costPerBw);

	WebDataCenter datacenter = null;
	try {
	    datacenter = new WebDataCenter(name, characteristics,
		    new VmAllocationPolicySimple(hostList), storageList, 0);
	} catch (Exception e) {
	    e.printStackTrace();
	}

	return datacenter;
    }

}