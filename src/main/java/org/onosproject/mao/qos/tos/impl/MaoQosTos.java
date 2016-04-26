/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.mao.qos.tos.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.core.Application;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mao.qos.api.impl.qdisc.MaoTbfQdiscObj;
import org.onosproject.mao.qos.tos.intf.MaoQosTosService;
import org.onosproject.mao.qos.api.impl.classify.MaoHtbClassObj;
import org.onosproject.mao.qos.api.impl.qdisc.MaoHtbQdiscObj;
import org.onosproject.mao.qos.api.intf.MaoQosObj;
import org.onosproject.mao.qos.intf.MaoQosService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.TcpPortCriterion;
import org.onosproject.net.flow.criteria.UdpPortCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
@Service
public class MaoQosTos implements MaoQosTosService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final long DEFAULT_QUEUE_ID = 0x10 - 1;
    private final long FTP_QUEUE_ID = 0x20 - 1;
    private final long STREAM_QUEUE_ID = 0x30 - 1;


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MaoQosService maoQosService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;


    private InnerPacketProcessor innerPacketProcessor;
    private InnerFlowRuleListener innerFlowRuleListener;
    private ApplicationId appId;


    // work with FWD
    @Activate
    protected void activate() {
        log.info("init...");

        appId = coreService.registerApplication("onos.app.mao.qos.tos");

        buildDefaultQos();

        innerFlowRuleListener = new InnerFlowRuleListener();
        flowRuleService.addListener(innerFlowRuleListener);

//        requestPacket();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("destroy...");

//        withdrawPacket();

        flowRuleService.removeListener(innerFlowRuleListener);
        innerFlowRuleListener = null;
        flowRuleService.removeFlowRulesById(appId);

        log.info("Stopped");
    }

    private void requestPacket() {
        innerPacketProcessor = new InnerPacketProcessor();
        packetService.addProcessor(innerPacketProcessor, PacketProcessor.director(100));

        TrafficSelector punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(80))
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.requestPackets(punt, PacketPriority.REACTIVE, appId);


        punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(22))
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.requestPackets(punt, PacketPriority.REACTIVE, appId);

        punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.requestPackets(punt, PacketPriority.REACTIVE, appId);
    }

    private void withdrawPacket() {
        TrafficSelector punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(80))
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.cancelPackets(punt, PacketPriority.REACTIVE, appId);


        punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(22)) // TODO - CHECK - FTP file transfer is server 22 ?
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.cancelPackets(punt, PacketPriority.REACTIVE, appId);

        punt = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .build();

        //TODO - CHECK - if need PacketPriority.CONTROL ?
        packetService.cancelPackets(punt, PacketPriority.REACTIVE, appId);

        packetService.removeProcessor(innerPacketProcessor);
        innerPacketProcessor = null;
    }


    private void buildDefaultQos() {
        buildOneDefaultQos(DeviceId.deviceId("of:0001111111111111"), 1);
        buildOneDefaultQos(DeviceId.deviceId("of:0001111111111111"), 3);
        buildOneDefaultQos(DeviceId.deviceId("of:0002222222222222"), 1);
        buildOneDefaultQos(DeviceId.deviceId("of:0002222222222222"), 2);
        buildOneDefaultQos(DeviceId.deviceId("of:0002222222222222"), 3);
        buildOneDefaultQos(DeviceId.deviceId("of:0002222222222222"), 4);
    }

    private void buildOneDefaultQos(DeviceId deviceId, int deviceIntfNumber) {

        MaoHtbQdiscObj rootHtb = MaoHtbQdiscObj.builder()
                .add()
                .setParent(MaoQosObj.ROOT)
                .setHandleOrClassId("1:")
                .setDeviceId(deviceId)
                .setDeviceIntfNumber(deviceIntfNumber)
                .build();
        maoQosService.Apply(rootHtb);

        MaoHtbClassObj parentHtbClass = MaoHtbClassObj.builder()
                .add()
                .setParent(rootHtb)
                .setHandleOrClassId("1:5")
                .setDeviceId(deviceId)
                .setDeviceIntfNumber(deviceIntfNumber)
                .rate(10, MaoQosObj.RATE_MBIT)
                .ceil(10, MaoQosObj.RATE_MBIT)
                .burst(10, MaoQosObj.SIZE_MBIT)
                .cburst(10, MaoQosObj.SIZE_MBIT)
                .build();
        maoQosService.Apply(parentHtbClass);

        MaoHtbClassObj leafHtbClass = MaoHtbClassObj.builder()
                .add()
                .setParent(parentHtbClass)
                .setHandleOrClassId("1:10")
                .setDeviceId(deviceId)
                .setDeviceIntfNumber(deviceIntfNumber)
                .rate(1, MaoQosObj.RATE_KBIT)
                .ceil(10, MaoQosObj.RATE_MBIT)
                .burst(10, MaoQosObj.SIZE_MBIT)
                .cburst(10, MaoQosObj.SIZE_MBIT)
                .build();
        maoQosService.Apply(leafHtbClass);

        MaoTbfQdiscObj leafTbf = MaoTbfQdiscObj.builder()
                .add()
                .setParent(leafHtbClass)
                .setHandleOrClassId("10:")
                .setDeviceId(deviceId)
                .setDeviceIntfNumber(deviceIntfNumber)
                .rate(10, MaoQosObj.RATE_MBIT)
                .burst(10, MaoQosObj.SIZE_MBIT)
                .limit(5, MaoQosObj.SIZE_MBYTE)
                .build();
        maoQosService.Apply(leafTbf);
    }


    private class InnerFlowRuleListener implements FlowRuleListener {

        @Override
        public void event(FlowRuleEvent event) {

            if (event.type().equals(FlowRuleEvent.Type.RULE_ADD_REQUESTED)) {

                FlowRule flowRule = event.subject();


                List queueInst = flowRule.treatment().allInstructions().stream()
                        .filter(inst -> inst.type().equals(Instruction.Type.QUEUE))
                        .collect(Collectors.toList());

                if (!queueInst.isEmpty()) {
                    return;
                }


                TrafficSelector trafficSelector = flowRule.selector();

                Criterion ipSrc = trafficSelector.getCriterion(Criterion.Type.IPV4_SRC);
                Criterion ipDst = trafficSelector.getCriterion(Criterion.Type.IPV4_DST);

                if (ipSrc == null && ipDst == null) {
                    return;
                }


                if (ipSrc != null && ((IPCriterion) ipSrc).ip().equals(IpPrefix.valueOf("10.0.0.1/32"))) {


                } else if (ipDst != null && ((IPCriterion) ipDst).ip().equals(IpPrefix.valueOf("10.0.0.1/32"))) {

                    Criterion tcpSrc = trafficSelector.getCriterion(Criterion.Type.TCP_SRC);
                    Criterion udpSrc = trafficSelector.getCriterion(Criterion.Type.UDP_SRC); // TODO - CHECK - IS THIS PORT?

                    if (tcpSrc != null && ((TcpPortCriterion) tcpSrc).tcpPort().toInt() == 22) {

                        // TODO - Create Qos
                        // TODO - CREATE SET_QUEUE
                    } else if (udpSrc != null && ((UdpPortCriterion) udpSrc).udpPort().toInt() == 1355) {

                        // TODO - Create Qos
                        // TODO - CREATE SET_QUEUE
                    }

                    return;
                }

                setDefaultQueue(flowRule);

            }
        }

        private void setDefaultQueue(FlowRule flowRule) {


            List<Instruction> outputInst = flowRule.treatment().allInstructions().stream()
                    .filter(inst -> inst.type().equals(Instruction.Type.OUTPUT))
                    .collect(Collectors.toList());
            if (outputInst.size() > 1) {
                log.warn("OUTPUT is more than 1 !!!");
            } else if (outputInst.isEmpty()) {
                log.warn("OUTPUT is not exist !!!");
            } else {

                PortNumber port = ((Instructions.OutputInstruction) outputInst.get(0)).port();
                long queueId = lookupDefaultQueueId(flowRule.deviceId(), port.toLong());

                flowRule.treatment().immediate().add(0, Instructions.setQueue(queueId, port));

                flowRuleService.applyFlowRules(flowRule);
            }
        }

        private long lookupDefaultQueueId(DeviceId deviceId, long deviceIntfNumber) {
            return DEFAULT_QUEUE_ID;
        }
    }

    private class InnerPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            Ethernet etherPkt = context.inPacket().parsed();
            if (etherPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            IPv4 ipPkt = (IPv4) etherPkt.getPayload();

            if (ipPkt.getProtocol() != IPv4.PROTOCOL_TCP && ipPkt.getProtocol() != IPv4.PROTOCOL_UDP) {
                return;
            }

            if (ipPkt.getProtocol() == IPv4.PROTOCOL_TCP) {

                if (!IpPrefix.valueOf(ipPkt.getSourceAddress(), 32).equals(IpPrefix.valueOf("10.0.0.1/32"))) {
                    return;
                }

                if (!IpPrefix.valueOf(ipPkt.getDestinationAddress(), 32).equals(IpPrefix.valueOf("10.0.1.0/24"))) {
                    return;
                }

                TCP tcpPkt = (TCP) ipPkt.getPayload();

                if (tcpPkt.getDestinationPort() == 80) {

                    // TODO - config QOS
                    // todo - push flow entries guide traffic

                } else if (tcpPkt.getDestinationPort() == 22) {

                } else {

                    return;
                }

            } else {

                if (!IpPrefix.valueOf(ipPkt.getDestinationAddress(), 32).equals(IpPrefix.valueOf("10.0.0.1/32"))) {
                    return;
                }

                if (!IpPrefix.valueOf(ipPkt.getSourceAddress(), 32).equals(IpPrefix.valueOf("10.0.1.3/32"))) {
                    return;
                }

                UDP udpPkt = (UDP) ipPkt.getPayload();

            }

        }
    }


    @Override
    public boolean pushQos() {

        MaoHtbQdiscObj.Builder maoHtbQdiscObjBuilder = MaoHtbQdiscObj.builder();

        maoHtbQdiscObjBuilder
                .add()
                .setDeviceId(DeviceId.deviceId("of:0001111111111111"))
                .setDeviceIntfNumber(3)
                .setParent(MaoQosObj.ROOT)
                .setHandleOrClassId("1:")
                .setDefaultId(2);

        MaoHtbQdiscObj htbRoot = maoHtbQdiscObjBuilder.build();


        MaoHtbClassObj.Builder maoHtbClassObjBuilder = MaoHtbClassObj.builder();

        maoHtbClassObjBuilder
                .add()
                .setDeviceId(DeviceId.deviceId("of:0001111111111111"))
                .setDeviceIntfNumber(3)
                .setParent(htbRoot)
                .setHandleOrClassId("1:1")
                .rate(1, MaoHtbClassObj.RATE_GBIT)
                .burst(20, MaoHtbClassObj.SIZE_MBYTE);

        MaoHtbClassObj htbRootClass = maoHtbClassObjBuilder.build();


        maoHtbClassObjBuilder
                .setParent(htbRootClass)
                .setHandleOrClassId("1:2")
                .rate(1, MaoHtbClassObj.RATE_GBIT)
                .burst(20, MaoHtbClassObj.SIZE_MBYTE);

        MaoHtbClassObj htbDefaultClass = maoHtbClassObjBuilder.build();


        maoHtbClassObjBuilder
                .setHandleOrClassId("1:3")
                .rate(30, MaoHtbClassObj.RATE_MBIT)
                .burst(20, MaoHtbClassObj.SIZE_KBYTE);

        MaoHtbClassObj htbLimitClass = maoHtbClassObjBuilder.build();


        maoHtbQdiscObjBuilder
                .setParent(htbDefaultClass)
                .setHandleOrClassId("10")
                .setDefaultId(MaoHtbQdiscObj.INVALID_INT);
        MaoHtbQdiscObj htbDefault = maoHtbQdiscObjBuilder.build();

        maoHtbQdiscObjBuilder
                .setParent(htbLimitClass)
                .setHandleOrClassId("20");
        MaoHtbQdiscObj htbLimit = maoHtbQdiscObjBuilder.build();

        boolean ret = false;
        ret = maoQosService.Apply(htbRoot);
        ret = maoQosService.Apply(htbRootClass);
        ret = maoQosService.Apply(htbDefaultClass);
        ret = maoQosService.Apply(htbLimitClass);
        ret = maoQosService.Apply(htbDefault);
        ret = maoQosService.Apply(htbLimit);


        return ret;
    }


}
