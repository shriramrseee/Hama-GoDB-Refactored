package in.dream_lab.goffish.hama.succinctstructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.cs.succinct.StorageMode;
import edu.berkeley.cs.succinct.buffers.SuccinctIndexedFileBuffer;
import in.dream_lab.goffish.api.IEdge;
import in.dream_lab.goffish.api.IRemoteVertex;
import in.dream_lab.goffish.api.ISubgraph;
import in.dream_lab.goffish.api.IVertex;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Writable;
import org.mortbay.log.Log;

public class SuccinctArraySubgraph12In<S extends Writable, V extends Writable, E extends Writable, I extends Writable, J extends Writable, K extends Writable> implements ISubgraph<S, V, E, I, J, K> {
    public Map<Long, Long> remotevertexToSubgraph;
    public Map<Long, Long> localvertexToSubgraph;
    public K subgraphId;
    S _value;
    public static final org.apache.commons.logging.Log Log = LogFactory.getLog(SuccinctArraySubgraph12In.class);
    private SuccinctIndexedFileBuffer vertexSuccinctBuffer;
    List<SuccinctIndexedFileBuffer> outEdgeSuccinctBufferList;
    List<SuccinctIndexedFileBuffer> inEdgeSuccinctBufferList;
    private HashMap<String, SuccinctIndexedFileBuffer> propertySuccinctBufferMap;
    private static Splitter splitter;
//    String vertexPath,edgePath;
    public SuccinctArraySubgraph12In(K subgraphId, SuccinctIndexedFileBuffer _vertexSuccinctBuffer,HashMap<String, SuccinctIndexedFileBuffer> propertySuccinctBufferMap,List<SuccinctIndexedFileBuffer> _outEdgeSuccinctBufferList,List<SuccinctIndexedFileBuffer> _inEdgeSuccinctBufferList)
    {

    	setVertexSuccinctBuffer(_vertexSuccinctBuffer);
    	outEdgeSuccinctBufferList=_outEdgeSuccinctBufferList;
    	inEdgeSuccinctBufferList=_inEdgeSuccinctBufferList;
    	this.setPropertySuccinctBufferMap(propertySuccinctBufferMap);
        this.subgraphId = subgraphId;
        splitter = Splitter.createSplitter();
    }
    
    public SuccinctIndexedFileBuffer getPropertyBuffer(String property)
    {
        return getPropertySuccinctBufferMap().get(property);
    }
    public SuccinctIndexedFileBuffer getOutEdgeBuffer(int index)
    {
        return outEdgeSuccinctBufferList.get(index);
    }
    
  
    public List<SuccinctIndexedFileBuffer> getOutEdgeBufferList()
    {
        return outEdgeSuccinctBufferList;
    }
    
    public SuccinctIndexedFileBuffer getInEdgeBuffer(int index)
    {
        return inEdgeSuccinctBufferList.get(index);
    }
    
  
    public List<SuccinctIndexedFileBuffer> getInEdgeBufferList()
    {
        return inEdgeSuccinctBufferList;
    }
    
    public void setRemoteMap(Map<Long, Long> v2s)
    {
        this.remotevertexToSubgraph = v2s;
    }
    
    public Map<Long, Long> getRemoteMap()
    {
        return remotevertexToSubgraph;
    }
   
    public K getSubgraphId()
    {
        return subgraphId;
    }
    public IVertex<V, E, I, J> getVertexById(I vertexId)
    {
        throw new UnsupportedOperationException("Function not supported");
    }
    public long getVertexCount()
    {
        throw new UnsupportedOperationException("We do not count the number of vertices");
    }
    public long getLocalVertexCount()
    {
        throw new UnsupportedOperationException("We do not count the number of local vertices");
    }
    public Iterable<IVertex<V, E, I, J>> getVertices()
    {
        throw new UnsupportedOperationException("We do not get the list of vertices from a subgraph");
    }
    public Iterable<IVertex<V, E, I, J>> getLocalVertices()
    {
        throw new UnsupportedOperationException("We do not get the list of local vertices");
    }
    public Iterable<IRemoteVertex<V, E, I, J, K>> getRemoteVertices()
    {
        throw new UnsupportedOperationException("We do not get the list of remote vertices");
    }
    public Iterable<IEdge<E, I, J>> getOutEdges()
    {
        throw new UnsupportedOperationException("We do not get the list of out edges");
    }
    public IEdge<E, I, J> getEdgeById(J edgeID)
    {
        throw new UnsupportedOperationException("We do not get an edge by its id");
    }
    @Override
    public void setSubgraphValue(S value) {
      _value = value;
    }

    @Override
    public S getSubgraphValue() {
     
	return _value;
    }
  
    //TODO:Change this..DONE
    public List<Long> getVertexByProp(String propName, String propValue)
    {
    	long start = System.nanoTime();
    	List<Long> vid = new ArrayList<>();
    	Log.info("getVertexByProp");
    	
    	SuccinctIndexedFileBuffer propBuffer= getPropertyBuffer(propName);
//    	Integer offset;
    	byte[] record;
    	LongArrayList tokens;
    	long startFine = System.nanoTime();
    	Integer[] recordID = propBuffer.recordSearchIds(("#"+propValue+"@").getBytes());
    	Log.info("Lookup record id(vertex): "+ (System.nanoTime() - startFine)+ " ns " + recordID.length);
    	for (Integer rid : recordID)
    	 {
    		startFine = System.nanoTime();
//    		offset = succinctIndexedVertexFileBuffer.getRecordOffset(rid);
//    		Log.info("Lookup record offset(vertex): " + (System.nanoTime() - startFine) + " ns");
//    		startFine = System.nanoTime();
    		record = vertexSuccinctBuffer.getRecordBytes(rid);
    		Log.info("Extract until(vertex): "+(System.nanoTime() - startFine) + " ns");
    		Log.info("# Extracted Bytes: " + record.length);
    		tokens=splitter.splitLong(record);
            for(long token : tokens) {
                vid.add(token);
            }

    		
    	}
    	
    	
    	Log.info("Querying Time:" + (System.nanoTime() - start));
    	return vid;
    }

	public SuccinctIndexedFileBuffer getVertexSuccinctBuffer() {
		return vertexSuccinctBuffer;
	}

	public void setVertexSuccinctBuffer(SuccinctIndexedFileBuffer vertexSuccinctBuffer) {
		this.vertexSuccinctBuffer = vertexSuccinctBuffer;
	}

	public HashMap<String, SuccinctIndexedFileBuffer> getPropertySuccinctBufferMap() {
		return propertySuccinctBufferMap;
	}

	public void setPropertySuccinctBufferMap(HashMap<String, SuccinctIndexedFileBuffer> propertySuccinctBufferMap) {
		this.propertySuccinctBufferMap = propertySuccinctBufferMap;
	}
}
