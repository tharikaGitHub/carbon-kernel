/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.clustering;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.wso2.carbon.clustering.config.ClusterConfiguration;
import org.wso2.carbon.clustering.config.membership.scheme.MulticastSchemeConfig;
import org.wso2.carbon.clustering.coordinator.CustomCoordinatedActivity;
import org.wso2.carbon.clustering.exception.ClusterConfigurationException;
import org.wso2.carbon.clustering.exception.ClusterInitializationException;
import org.wso2.carbon.clustering.exception.MessageFailedException;
import org.wso2.carbon.clustering.internal.CarbonCluster;
import org.wso2.carbon.clustering.internal.ClusterUtil;
import org.wso2.carbon.clustering.internal.DataHolder;
import org.wso2.carbon.clustering.message.CustomClusterMessage;
import org.wso2.carbon.clustering.message.CustomMemberClusterMessage;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;


public class MulitcastMembershipSchemeTestCase extends MembershipSchemeBaseTest {

    @BeforeTest
    public void setup() throws ClusterConfigurationException {
        setupMembershipScheme("cluster-01.xml", "cluster-02.xml");
    }

    @Test(groups = {"wso2.carbon.clustering"},
          description = "test multicast scheme with two members")
    public void testMulticastMembershipScheme()
            throws ClusterInitializationException, ClusterConfigurationException {
        DataHolder instance = DataHolder.getInstance();
        CustomCoordinatedActivity coordinator1 = new CustomCoordinatedActivity("coordinator1");
        instance.addCoordinatedActivity(coordinator1);
        CustomCoordinatedActivity coordinator2 = new CustomCoordinatedActivity("coordinator2");
        instance.addCoordinatedActivity(coordinator2);
        CustomCoordinatedActivity coordinator3 = new CustomCoordinatedActivity("coordinator3");
        assertFalse(coordinator1.isExecutionComplete());
        assertFalse(coordinator2.isExecutionComplete());
        assertFalse(coordinator3.isExecutionComplete());

        initializeMembershipScheme();
        int noOfMembers = getNoOfMembers();
        assertEquals(noOfMembers, 2);

        ClusterConfiguration clusterConfiguration = getClusterContext().getClusterConfiguration();
        Object membershipScheme = clusterConfiguration.
                getMembershipSchemeConfiguration().getMembershipScheme();
        assertEquals(ClusterUtil.getMembershipScheme(clusterConfiguration),
                membershipScheme.toString());

        MulticastSchemeConfig multicastSchemeConfig = (MulticastSchemeConfig) membershipScheme;

        assertEquals(multicastSchemeConfig.getGroup(), "228.0.0.4");
        assertEquals(multicastSchemeConfig.getPort(), 45564);
        assertEquals(multicastSchemeConfig.getTimeout(), 0);
        assertEquals(multicastSchemeConfig.getTtl(), 100);
        assertTrue(coordinator1.isExecutionComplete());
        assertTrue(coordinator2.isExecutionComplete());
        assertFalse(coordinator3.isExecutionComplete());

    }

    @Test(groups = {"wso2.carbon.clustering"},
          description = "test send message with multicast scheme",
          dependsOnMethods = {"testMulticastMembershipScheme"})
    public void testSendMessage() throws MessageFailedException {
        CarbonCluster carbonCluster = getClusterService();
        CustomClusterMessage clusterMessage = new CustomClusterMessage("MulticastMessage");
        carbonCluster.sendMessage(clusterMessage);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            //ignore
        }
        assertEquals(clusterMessage.getExecutedMsg(), "MulticastMessageExecuted");
    }

    @Test(groups = {"wso2.carbon.clustering"},
          description = "test send message to specific member in multicast scheme",
          dependsOnMethods = {"testSendMessage"})
    public void testSendMessageToMember() throws MessageFailedException {
        CarbonCluster carbonCluster = getClusterService();
        List<ClusterMember> clusterMembers = carbonCluster.getMembers();
        List<ClusterMember> membersToSend = new ArrayList<>();
        for (ClusterMember member : clusterMembers) {
            if (member.getPort() == 4002) {
                membersToSend.add(member);
                break;
            }
        }
        if (!membersToSend.isEmpty()) {
            CustomMemberClusterMessage clusterMessage =
                    new CustomMemberClusterMessage("MulticastMemberMessage");
            carbonCluster.sendMessage(clusterMessage, membersToSend);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                //ignore
            }

            // try again, if the message was not delivered during the given sleep time
            if ("MulticastMessageExecuted".equals(clusterMessage.getExecutedMsg())) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
            // if still not delivered then fail the test
            if ("MulticastMessageExecuted".equals(clusterMessage.getExecutedMsg())) {
                fail("Message is not sent/executed with given time delay of 15 seconds");
            }
            assertEquals(clusterMessage.getExecutedMsg(), "MulticastMemberMessageExecuted");
        } else {
            fail("Members to send list is empty");
        }
    }

    @AfterTest
    public void shutdownNodes() {
        terminate();
    }
}
