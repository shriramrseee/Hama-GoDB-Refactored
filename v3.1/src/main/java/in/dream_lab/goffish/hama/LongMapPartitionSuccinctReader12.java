package in.dream_lab.goffish.hama;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hama.HamaConfiguration;
import org.apache.hama.bsp.BSPPeer;
import org.apache.hama.bsp.BSPPeerImpl;
import org.apache.hama.bsp.sync.SyncException;
import org.apache.hama.commons.util.KeyValuePair;
import org.apache.hama.util.ReflectionUtils;
import org.apache.tools.ant.taskdefs.PathConvert.MapEntry;

import com.google.gson.*;

import edu.berkeley.cs.succinct.StorageMode;
import edu.berkeley.cs.succinct.buffers.SuccinctIndexedFileBuffer;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import in.dream_lab.goffish.api.IEdge;
import in.dream_lab.goffish.api.ISubgraph;
import in.dream_lab.goffish.api.IVertex;
import in.dream_lab.goffish.godb.MapValue;
import in.dream_lab.goffish.hama.api.IControlMessage;
import in.dream_lab.goffish.hama.api.IReader;
import in.dream_lab.goffish.hama.succinctstructure.ConstructSuccinctFile;
import in.dream_lab.goffish.hama.succinctstructure.SuccinctArraySubgraph;
import in.dream_lab.goffish.hama.succinctstructure.SuccinctArraySubgraph12;
import in.dream_lab.goffish.hama.succinctstructure.SuccinctHashMapSubgraph;
import in.dream_lab.goffish.hama.succinctstructure.SuccinctSubgraph;
import in.dream_lab.goffish.hama.utils.DisjointSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import in.dream_lab.goffish.api.IMessage;
import in.dream_lab.goffish.api.IRemoteVertex;

/*
 * This is a reader that will read a set of succinct files for both vertices and edges.
 * This reader will also read two additional files that will have mapping of subgraph to partition,remoteVertices to subgraphs
 * 
 * DummyFile is to be passed which contains pseudo partition id
 * 
 */

public class LongMapPartitionSuccinctReader12<S extends Writable, V extends Writable, E extends Writable, K extends Writable, M extends Writable>
    implements
    IReader<Writable, Writable, Writable, Writable, S, V, E, LongWritable, LongWritable, LongWritable> {

  HamaConfiguration conf;
  BSPPeer<Writable, Writable, Writable, Writable, Message<K, M>> peer;
  private Map<K, Integer> subgraphPartitionMap;// this map is loaded from a file
  private Map<Long, Long> vertexSubgraphMap = new HashMap<>();
  private Map<String, String[]> blockPartFileMap = new HashMap<>();
  private Map<Long,Long> remoteVertexToSubgraphMap= new HashMap<>();//this map is loaded from a text file
  private Set<String> vertexPropertySet= new HashSet<String>();
  private Set<String> edgePropertySet=new HashSet<String>();
  
  JsonParser GsonParser = new JsonParser();
  public LongMapPartitionSuccinctReader12(
      BSPPeer<Writable, Writable, Writable, Writable, Message<K, M>> peer,
              Map<K, Integer> subgraphPartitionMap) {
    this.peer = peer;
    this.subgraphPartitionMap = subgraphPartitionMap; 
    this.conf = peer.getConfiguration();
//    this.vertexSubgraphMap = new HashMap<LongWritable, LongWritable>();
//    this.vertexPropertySet.add("patid");
//    this.vertexPropertySet.add("nclass");
//    this.vertexPropertySet.add("country");
  }
  
  public static final Log LOG = LogFactory.getLog(LongMapPartitionSuccinctReader12.class);
  Integer pseudoPartId=null;
  String vertexDataDir=null, edgeDataDir=null;
  @Override
  public List<ISubgraph<S, V, E, LongWritable, LongWritable, LongWritable>> getSubgraphs()
      throws IOException, SyncException, InterruptedException {
          LOG.info("Reading Succinct Subgraphs");


    // List of edges.Used to create RemoteVertices
    //List<IEdge<E, LongWritable, LongWritable>> _edges = new ArrayList<IEdge<E, LongWritable, LongWritable>>();
    
    KeyValuePair<Writable, Writable> pair;
    pair=peer.readNext();
    String[] temp=pair.getValue().toString().split(";");
    pseudoPartId=Integer.parseInt(temp[0]);
    vertexDataDir=temp[1];
    edgeDataDir= temp[2];
    
//Json creation of vertices... not required for succinct reader    
//    while ((pair = peer.readNext()) != null) {
//      String StringJSONInput = pair.getValue().toString();
//      
//      
//        Vertex<V, E, LongWritable, LongWritable> vertex = createVertex(
//            StringJSONInput);
//        vertexMap.put(vertex.getVertexId(), vertex);
//        //_edges.addAll(vertex.getOutEdges());
//      }
    
//    List<IVertex<V, E, LongWritable, LongWritable>> vertices = Lists
//        .newArrayList();
//    for (IVertex<V, E, LongWritable, LongWritable> vertex : vertexMap
//        .values()) {
//      vertices.add(vertex);
//    }
//
//    /* Create remote vertex objects. */
////    int remoteVertices = 0;
//    for (IVertex<V, E, LongWritable, LongWritable> vertex : vertices) {
//      for (IEdge<E, LongWritable, LongWritable> e : vertex.getOutEdges()) {
//        LongWritable sinkID = e.getSinkVertexId();
//        IVertex<V, E, LongWritable, LongWritable> sink = vertexMap.get(sinkID);
//        if (sink == null) {
//          sink = new RemoteVertex<V, E, LongWritable, LongWritable, LongWritable>(
//              sinkID);
//          vertexMap.put(sinkID, (IRemoteVertex<V, E, LongWritable, LongWritable,LongWritable>)sink);
////          remoteVertices++;
//        }
//      }
//    }
//    int local = 0,remote =0;
//    for (IVertex<V, E, LongWritable, LongWritable> vertex : vertexMap.values()) {
//      if (!vertex.isRemote()) {
//        local ++;
//      }
//      else
//        remote++;
//    }
//    System.out.println("Local = "+local +" Remote = "+remote);
    /*
    for (IEdge<E, LongWritable, LongWritable> e : _edges) {
      LongWritable sinkID = e.getSinkVertexId();
      IVertex<V, E, LongWritable, LongWritable> sink =  vertexMap.get(sinkID);
      if (sink == null) {
        sink = new RemoteVertex<V, E, LongWritable, LongWritable, LongWritable>(sinkID);
        vertexMap.put(sinkID, sink);
      }
    }*/
    
    //Direct Copy paste from here
    //TODO: pass  pid instead of peerindex
    
    Partition<S, V, E, LongWritable, LongWritable, LongWritable> partition = new Partition<S, V, E, LongWritable, LongWritable, LongWritable>(peer.getPeerIndex());
//    Partition<S, V, E, LongWritable, LongWritable, LongWritable> classicPartition = new Partition<S, V, E, LongWritable, LongWritable, LongWritable>(peer.getPeerIndex());
    Collection<IVertex<V, E, LongWritable, LongWritable>> dummyVertices=null;
    formSubgraphs(partition, dummyVertices);
//THIS PART NOT REQUIRED AS NOW WE ARE ONLY CONCERNED    
//    /*
//     * Ask Remote vertices to send their subgraph IDs. Requires 2 supersteps
//     * because the graph is directed
//     */
//    Message<LongWritable, LongWritable> question = new Message<LongWritable, LongWritable>();
//    ControlMessage controlInfo = new ControlMessage();
//    controlInfo.setTransmissionType(IControlMessage.TransmissionType.BROADCAST);
//    question.setControlInfo(controlInfo);
//    /*
//     * Message format being sent:
//     * partitionID remotevertex1 remotevertex2 ...
//     */
////    int n=0;
//    byte partitionIDbytes[] = Ints.toByteArray(peer.getPeerIndex());
//    controlInfo.addextraInfo(partitionIDbytes);
//    for (IVertex<V, E, LongWritable, LongWritable> v : vertexMap.values()) {
//      if (v instanceof RemoteVertex) {
////        n++;
//        byte vertexIDbytes[] = Longs.toByteArray(v.getVertexId().get());
//        controlInfo.addextraInfo(vertexIDbytes);
//      }
//    }
//    
////    if (n != remoteVertices) {
////      System.out.println("Something wrong with remote vertex broadcast");
////    }
//    sendToAllPartitions(question);

//    peer.sync();
//    
//    Message<LongWritable, LongWritable> msg;
//    //Receiving 1 message per partition
//    while ((msg = (Message<LongWritable, LongWritable>) peer.getCurrentMessage()) != null) {
//      /*
//       * Subgraph Partition mapping broadcast
//       * Format of received message:
//       * partitionID subgraphID1 subgraphID2 ...
//       */
//      if (msg.getMessageType() == Message.MessageType.SUBGRAPH) {
//        Iterable<BytesWritable> subgraphList = ((ControlMessage) msg
//            .getControlInfo()).getExtraInfo();
//        
//        Integer partitionID = Ints.fromByteArray(subgraphList.iterator().next().getBytes());
//        
//        for (BytesWritable subgraphListElement : Iterables.skip(subgraphList,1)) {
//          LongWritable subgraphID = new LongWritable(
//              Longs.fromByteArray(subgraphListElement.getBytes()));
//          subgraphPartitionMap.put((K) subgraphID, partitionID);
//        }
//        continue;
//      }
//      
//      /*
//       * receiving query to find subgraph id Remote Vertex
//       */
//      Iterable<BytesWritable> RemoteVertexQuery = ((ControlMessage) msg
//          .getControlInfo()).getExtraInfo();
//      
//      /*
//       * Reply format :
//       * sinkID1 subgraphID1 sinkID2 subgraphID2 ...
//       */
//      Message<LongWritable, LongWritable> subgraphIDReply = new Message<LongWritable, LongWritable>(); 
//      controlInfo = new ControlMessage();
//      controlInfo.setTransmissionType(IControlMessage.TransmissionType.NORMAL);
//      subgraphIDReply.setControlInfo(controlInfo);
//      
//      Integer sinkPartition = Ints.fromByteArray(RemoteVertexQuery.iterator().next().getBytes());
//      boolean hasAVertex = false;
//      for (BytesWritable remoteVertex : Iterables.skip(RemoteVertexQuery,1)) {
//        LongWritable sinkID = new LongWritable(Longs.fromByteArray(remoteVertex.getBytes()));
//        LongWritable sinkSubgraphID = vertexSubgraphMap.get(sinkID);
//        //In case this partition does not have the vertex 
//        /* Case 1 : If vertex does not exist
//         * Case 2 : If vertex exists but is remote, then its subgraphID is null
//         */
//        if (sinkSubgraphID == null) {
//          continue;
//        }
//        hasAVertex = true;
//        byte sinkIDbytes[] = Longs.toByteArray(sinkID.get());
//        controlInfo.addextraInfo(sinkIDbytes);
//        byte subgraphIDbytes[] = Longs.toByteArray(sinkSubgraphID.get());
//        controlInfo.addextraInfo(subgraphIDbytes);
//      }
//      if (hasAVertex) {
//        peer.send(peer.getPeerName(sinkPartition.intValue()),
//            (Message<K, M>) subgraphIDReply);
//      }
//    }
//    peer.sync();
    
//    int remoteV = 0;
//    while ((msg = (Message<LongWritable, LongWritable>)peer.getCurrentMessage()) != null) {
//      Iterable<BytesWritable> remoteVertexReply = ((ControlMessage) msg
//          .getControlInfo()).getExtraInfo();
//      
//      Iterator<BytesWritable> queryResponse = remoteVertexReply.iterator();
//      
//      while(queryResponse.hasNext()) {
//        LongWritable sinkID = new LongWritable(Longs.fromByteArray(queryResponse.next().getBytes()));
//        LongWritable remoteSubgraphID = new LongWritable(Longs.fromByteArray(queryResponse.next().getBytes()));
//        RemoteVertex<V, E, LongWritable, LongWritable, LongWritable> sink =(RemoteVertex<V, E, LongWritable, LongWritable, LongWritable>) vertexMap.get(sinkID);
//        if (sink == null) {
//          System.out.println("NULL"+sink);
//        }
//        remoteV++;
////        System.out.println("Setting subgraph id for remote vertex");
//        sink.setSubgraphID(remoteSubgraphID);
//        remoteVertexToSubgraphMap.put(sink.vertexID.get(), remoteSubgraphID.get());
//        
//      }
//      LOG.info("READERMAPSIZE:"+ remoteVertexToSubgraphMap.size());
//    }
//TODO: create logical partition to peerIndex mapping in two supersteps    
 
  Message<LongWritable, LongWritable> question = new Message<LongWritable, LongWritable>();
  ControlMessage controlInfo = new ControlMessage();
  controlInfo.setTransmissionType(IControlMessage.TransmissionType.BROADCAST);
  question.setControlInfo(controlInfo);
    /*
  * Message format being sent:
  * logicalPartitionID peerIndex 
  */

 byte partitionIDbytes[] = Ints.toByteArray(peer.getPeerIndex());
 byte logicalPartitionIDBytes[] = Ints.toByteArray(pseudoPartId);
 controlInfo.addextraInfo(logicalPartitionIDBytes);
 controlInfo.addextraInfo(partitionIDbytes);
 
 
 sendToAllPartitions(question);    
    
 peer.sync();
 
 //Receiving Back..hacking pseudo partitionid as subgraphid
 
 Map<K,Integer> logicalToPeerMapping= new HashMap<K,Integer>();
Message<LongWritable, LongWritable> msg;
//Receiving 1 message per partition
while ((msg = (Message<LongWritable, LongWritable>) peer.getCurrentMessage()) != null) {
	
  Iterable<BytesWritable> remoteMessage = ((ControlMessage) msg
  .getControlInfo()).getExtraInfo();

  Iterator<BytesWritable> mIter=remoteMessage.iterator();  
		
  Integer logicalPartitionId=Ints.fromByteArray(mIter.next().getBytes());
  Integer peerIndex = Ints.fromByteArray(mIter.next().getBytes());
  subgraphPartitionMap.put((K)new LongWritable(logicalPartitionId.longValue()), peerIndex);

}
 
//subgraphPartitionMap=logicalToPeerMapping;
 LOG.info("SubgraphToPartitionMapping:");
 
 for(Entry<K, Integer> entry:subgraphPartitionMap.entrySet()) {
	 LOG.info("SPMAP:"+ entry.getKey().toString() + "," + entry.getValue().toString());
 }
  //TODO:read SubgraphPartitionMap from a file and populate it here   ... removed because of a hack... FIXME
// String spmFile="/scratch/SynthGraphPart/PartitiontoSubgraphMapping/spmFile"; 
//LOG.info("Populating Subgraph to Partition Mapping");
long start=System.currentTimeMillis();
// FileReader fr = new FileReader(spmFile);
// BufferedReader br = new BufferedReader(fr);
//
// String sCurrentLine;
//
// while ((sCurrentLine = br.readLine()) != null) {
//	 String pData = sCurrentLine.substring(1, sCurrentLine.length()-1);
//	 String data[]=pData.split(",");
//	 int pid=Integer.parseInt(data[0]);
//	 String sdata=data[1].substring(1, data[1].length()-1);
//	 String subArray[]=sdata.split(",");
//	 for(String subgraph:subArray) {
//		 subgraphPartitionMap.put((K)new LongWritable(Long.parseLong(subgraph)), logicalToPeerMapping.get(pid));
//	 }
// }
// 
// br.close();
 LOG.info("Populating subgraph to Partition Time:" + (System.currentTimeMillis()-start));
 
//TODO:Read remoteVertexToSubgraph here and populate the object
 LOG.info("Populating remote Vertex to Subgraph Mapping");
 start=System.currentTimeMillis();
 String rvsmFile= vertexDataDir + "RemoteVertex/rvsmFile" + pseudoPartId; 

 FileReader fr1 = new FileReader(rvsmFile);
 BufferedReader br1 = new BufferedReader(fr1);
String sCurrentLine=null;

 while ((sCurrentLine = br1.readLine()) != null) {
	 String pData = sCurrentLine.substring(sCurrentLine.indexOf(',')+2, sCurrentLine.length()-2);
	 String[] data= pData.split(",\\s+");
	 

		
	
	 for(String tuple:data) {
		 try {
		 String[] rtuple=tuple.substring(1, tuple.length()-1).split(",");
		 remoteVertexToSubgraphMap.put(Long.parseLong(rtuple[1]), Long.parseLong(rtuple[0]));
		 
		 }catch(Exception e) {
			 LOG.info("Exception in Line:"+sCurrentLine+"  Exception:" + e.getMessage());
		 }
	 }
	
 }
 
 br1.close();
 
 LOG.info("Populating remote vertex data Time:" + (System.currentTimeMillis()-start));
 //TODO: Read localVertexToSubgraph File and populate the object.. NO LONGER REQUIRED
// String lvsmFile="/home/abhilash/lvsmFile.txt" + pseudoPartId; 
//
// FileReader fr2 = new FileReader(lvsmFile);
// BufferedReader br2 = new BufferedReader(fr2);
//
//
// while ((sCurrentLine = br2.readLine()) != null) {
//	 String pData = sCurrentLine.substring(1, sCurrentLine.length()-1);
//	 String data[]=pData.split(",");
//	 long sid=Integer.parseInt(data[0]);
//	 String sdata=data[1].substring(1, data[1].length()-1);
//	 String vArray[]=sdata.split(",");
//	 for(String vertex:vArray) {
//		 vertexSubgraphMap.put(Long.parseLong(vertex), sid);
//	 }
// }
// 
// br2.close();

 
 
 //finally populate the subgraphs and return the answer   
    for(ISubgraph<S, V, E, LongWritable, LongWritable, LongWritable> sg:partition.getSubgraphs()) {
    	SuccinctArraySubgraph12<S, V, E, LongWritable, LongWritable, LongWritable> succinctArraySubgraph12=(SuccinctArraySubgraph12<S, V, E, LongWritable, LongWritable, LongWritable>)sg;
    	succinctArraySubgraph12.setRemoteMap(remoteVertexToSubgraphMap);
//    	succinctHashMapSubgraph.setLocalMap(vertexSubgraphMap);
    }
    
    return partition.getSubgraphs();
  }
  
  /* takes partition and message list as argument and sends the messages to their respective partition.
   * Needed to send messages just before peer.sync(),as a hama bug causes the program to stall while trying
   * to send and recieve(iterate over recieved message) large messages at the same time
   */
  private void sendMessage(int partition,
      List<Message<LongWritable, LongWritable>> messageList) throws IOException {
    
    for (Message<LongWritable, LongWritable> message : messageList) {
      peer.send(peer.getPeerName(partition), (Message<K, M>)message);
    }
    
  }
  
  private void sendToAllPartitions(Message<LongWritable, LongWritable> message) throws IOException {
    for (String peerName : peer.getAllPeerNames()) {
      peer.send(peerName, (Message<K, M>) message);
    }
  }

  //Creating of vertex using json entry...no longer used
//  @SuppressWarnings("unchecked")
//  Vertex<V, E, LongWritable, LongWritable> createVertex(String JSONString) {
//    JsonArray JSONInput = GsonParser.parse(JSONString).getAsJsonArray();
//
//    LongWritable sourceID = new LongWritable(
//        Long.valueOf(JSONInput.get(0).toString()));
//    assert (vertexMap.get(sourceID) == null);
//    
//    
//    //fix this
//  //assumed value of jsonMap= "key1:type1:value1$ key2:type2:value2$....."
//    //type could be Long or String or Double
//    String jsonMap=JSONInput.get(1).toString();
//    jsonMap=jsonMap.substring(1, jsonMap.length()-1);
////    LOG.info("JSONMAP:" + jsonMap);
//    String[] vprop=jsonMap.split(Pattern.quote("$"));
//    //key,value property pairs for a vertex
//    MapValue vertexValueMap=new MapValue();
//    for(int i=0;i<vprop.length;i++){
//        String[] map=vprop[i].split(Pattern.quote(":"));
//          
////        LOG.info("HashMap:" + map[0] + "," + map[2]);
//        if(vertexPropertySet.contains(map[0])){
//          
//            vertexValueMap.put(map[0], map[2]);
////            LOG.info("Entered HashMap:" + map[0] + "," + map[2]);
//        }
//        
//    }
//    
//    V vertexValue = (V) vertexValueMap;
//    
//
//    List<IEdge<E, LongWritable, LongWritable>> _adjList = new ArrayList<IEdge<E, LongWritable, LongWritable>>();
//    JsonArray edgeList = (JsonArray) JSONInput.get(2);
//    for (Object edgeInfo : edgeList) {
//      JsonArray edgeValues = ((JsonArray) edgeInfo).getAsJsonArray();
//      LongWritable sinkID = new LongWritable(
//          Long.valueOf(edgeValues.get(0).toString()));
//      LongWritable edgeID = new LongWritable(
//          Long.valueOf(edgeValues.get(1).toString()));
//      //fix this
//      //same format as vertex
//      String[] eprop= edgeValues.get(2).toString().split(Pattern.quote("$"));
////      MapWritable edgeMap=new MapWritable();
////      for(int i=0;i<eprop.length;i++){
////        String[] map=eprop[i].split(Pattern.quote(":"));
////        Text key=null;
////        Text value=null;
////        if(edgePropertySet.contains(map[0])){
////           key=new Text(map[0]);
////        //FIXME:assuming String values for now
////           value=new Text(map[2]);
////           edgeMap.put(key, value);
////        }
//        
////      }
//      
//      Edge<E, LongWritable, LongWritable> edge = new Edge<E, LongWritable, LongWritable>(
//          edgeID, sinkID);
//      
////      E edgeValue = (E) edgeMap;
//      edge.setValue(null);
//      _adjList.add(edge);
//      
//    }
//  
//    Vertex<V, E, LongWritable, LongWritable> vertex = new Vertex<V, E, LongWritable, LongWritable>(
//        sourceID,_adjList);
//    vertex.setValue(vertexValue);
//    return vertex; 
//  }
  
  /* Forms subgraphs by finding (weakly) connected components. */
  void formSubgraphs(Partition<S, V, E, LongWritable, LongWritable, LongWritable> partition, Collection<IVertex<V, E, LongWritable, LongWritable>> vertices) throws IOException {
    
    long subgraphCount = 0;
    Message<LongWritable, LongWritable> subgraphLocationBroadcast = new Message<LongWritable, LongWritable>();
    
    subgraphLocationBroadcast.setMessageType(Message.MessageType.SUBGRAPH);
    ControlMessage controlInfo = new ControlMessage();
    controlInfo
        .setTransmissionType(IControlMessage.TransmissionType.BROADCAST);
    subgraphLocationBroadcast.setControlInfo(controlInfo);
    
    byte partitionBytes[] = Ints.toByteArray(peer.getPeerIndex());
    controlInfo.addextraInfo(partitionBytes);
    
//        // initialize disjoint set.. subgraph formation not used
//    DisjointSets<IVertex<V, E, LongWritable, LongWritable>> ds = new DisjointSets<IVertex<V, E, LongWritable, LongWritable>>(
//        vertices.size());
//    for (IVertex<V, E, LongWritable, LongWritable> vertex : vertices) {
//      ds.addSet(vertex);
//    }
//
//    // union edge pairs
//    for (IVertex<V, E, LongWritable, LongWritable> vertex : vertices) {
//      if (!vertex.isRemote()) {
//        for (IEdge<E, LongWritable, LongWritable> edge : vertex.getOutEdges()) {
//          IVertex<V, E, LongWritable, LongWritable> sink = vertexMap
//              .get(edge.getSinkVertexId());
//          ds.union(vertex, sink);
//        }
//      }
//    }
//
//    Collection<? extends Collection<IVertex<V, E, LongWritable, LongWritable>>> components = ds
//        .retrieveSets();
//TODO: read partFile to BlockId mappings...NOT REQUIRED NOW
//    String bpmFile="/home/abhilash/bpmFile"; 
//    String sCurrentLine;
//    FileReader fr2 = new FileReader(bpmFile);
//    BufferedReader br2 = new BufferedReader(fr2);
//
//
//    while ((sCurrentLine = br2.readLine()) != null) {
//   	 String pData = sCurrentLine.substring(1, sCurrentLine.length()-1);
//   	 String data[]=pData.split(",");
//   	 long partFileNo=Integer.parseInt(data[0]);
//   	 String sdata=data[1].substring(1, data[1].length()-1);
//   	 String vArray[]=sdata.split(",");
//   	 
//   	 blockPartFileMap.put("part"+partFileNo,vArray);
//  
//    }
//    
//    br2.close();
    
   LOG.info("Graph formulation started");
    String vdirectory = vertexDataDir+"RGraphVertex"+pseudoPartId;
    File[] vfiles = new File(vdirectory).listFiles();
    Arrays.sort(vfiles);
    String edirectory = edgeDataDir+"RGraphOutEdge"+pseudoPartId;
    File[] efiles = new File(edirectory).listFiles();
    Arrays.sort(efiles);
    
    SuccinctIndexedFileBuffer vertexSuccinctFile=null;
    HashMap<String, SuccinctIndexedFileBuffer> propertySuccinctBufferMap = new HashMap<String, SuccinctIndexedFileBuffer>();
    List<SuccinctIndexedFileBuffer> edgeSuccinctBufferList = new ArrayList<SuccinctIndexedFileBuffer>();
    LongWritable logicalsubgraphID = new LongWritable(
            subgraphCount++ | (((long) pseudoPartId) << 32));
    for (int i=0;i <vfiles.length;i++ ) {
  
      //Reads a partition 
      //TODO:remove hard coding later
//      ConstructSuccinctFile.construct("/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "VertexData", "/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "VertexData.succinct");
//      ConstructSuccinctFile.construct("/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "edgeData", "/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "edgeData.succinct");
    	String vertexPath=vfiles[i].getAbsolutePath();
    	LOG.info("Vertex Succinct Path:" + vertexPath);
//    	String edgePath=efiles[i].getAbsolutePath();
//    	LOG.info("Edge Succinct Path:" + edgePath);
    	SuccinctIndexedFileBuffer succinctIndexedVertexFileBuffer = new SuccinctIndexedFileBuffer(vertexPath, StorageMode.MEMORY_ONLY);
    	String propName=vertexPath.split("_")[1];
    	
    	if(propName.equals("vid")) {
    		vertexSuccinctFile=succinctIndexedVertexFileBuffer;
    		propertySuccinctBufferMap.put(propName, succinctIndexedVertexFileBuffer);
    		
    	}else {
    		propertySuccinctBufferMap.put(propName, succinctIndexedVertexFileBuffer);
    	}
    	
    }//component loop ends
    
    
    for (int i=0;i <efiles.length;i++ ) {
//    LongWritable subgraphID = new LongWritable(
//            subgraphCount++ | (((long) pseudoPartId) << 32));
      //Reads a partition 
      //TODO:remove hard coding later
//      ConstructSuccinctFile.construct("/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "VertexData", "/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "VertexData.succinct");
//      ConstructSuccinctFile.construct("/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "edgeData", "/home/abhilash/SuccinctSubgraphFiles/Sub"+subgraphID.get() + "edgeData.succinct");
//    	String vertexPath=vfiles[i].getAbsolutePath();
//    	LOG.info("Vertex Succinct Path:" + vertexPath);
    	String edgePath=efiles[i].getAbsolutePath();
    	LOG.info("Edge Succinct Path:" + edgePath);
//    	SuccinctIndexedFileBuffer succinctIndexedVertexFileBuffer = new SuccinctIndexedFileBuffer(vertexPath, StorageMode.MEMORY_ONLY);
        SuccinctIndexedFileBuffer succinctIndexedEdgeFileBuffer = new SuccinctIndexedFileBuffer(edgePath, StorageMode.MEMORY_ONLY);
    	
//        String partFile=vfiles[i].getName().split("Vertex")[0];

//        	vertexSuccinctBufferList.add( succinctIndexedVertexFileBuffer);
        	edgeSuccinctBufferList.add( succinctIndexedEdgeFileBuffer);
      
//      classicPartition.addSubgraph(classicSubgraph);
//      byte subgraphIDbytes[] = Longs.toByteArray(subgraphID.get());
//      controlInfo.addextraInfo(subgraphIDbytes); 
//     
    }
      LongWritable pid = new LongWritable(pseudoPartId);
      SuccinctArraySubgraph12<S, V, E, LongWritable, LongWritable, LongWritable> subgraph = new SuccinctArraySubgraph12(pid,vertexSuccinctFile,propertySuccinctBufferMap,edgeSuccinctBufferList);
      partition.addSubgraph(subgraph);
//    sendToAllPartitions(subgraphLocationBroadcast);
      LOG.info("Graph formulation complete");
  }
  
}
