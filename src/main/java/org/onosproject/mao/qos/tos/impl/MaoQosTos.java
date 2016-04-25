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
import org.onosproject.mao.qos.tos.intf.MaoQosTosService;
import org.onosproject.mao.qos.api.impl.classify.MaoHtbClassObj;
import org.onosproject.mao.qos.api.impl.qdisc.MaoHtbQdiscObj;
import org.onosproject.mao.qos.api.intf.MaoQosObj;
import org.onosproject.mao.qos.intf.MaoQosService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
@Service
public class MaoQosTos implements MaoQosTosService {

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MaoQosService maoQosService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;


    private InnerPacketProcessor innerPacketProcessor;
    private ApplicationId appId;


    // work with FWD
    @Activate
    protected void activate() {
        log.info("init...");

        appId = coreService.registerApplication("onos.app.mao.qos.tos");

        requestPacket();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("destroy...");

        withdrawPacket();

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
                
                if(tcpPkt.getDestinationPort() == 80){

                    // TODO - config QOS
                    // todo - push flow entries guide traffic

                } else if (tcpPkt.getDestinationPort() == 22){

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
