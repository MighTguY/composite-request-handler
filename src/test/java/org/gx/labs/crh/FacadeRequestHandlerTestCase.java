package org.gx.labs.crh;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.junit.Before;
import org.junit.Test;
/**
 * Unit test for {@link FacadeRequestHandler}.
 * 
 * @author agazzarini
 * @since 1.0
 */
public class FacadeRequestHandlerTestCase extends BaseUnitTest {
	@Before
	public void setUp() {
		rh1 = mock(SearchHandler.class);
		rh2 = mock(SearchHandler.class);
		rh3 = mock(SearchHandler.class);
		
		cut = new FacadeRequestHandler();
		
		qrequest = mock(SolrQueryRequest.class);
		qresponse = mock(SolrQueryResponse.class);
		
		args = new SimpleOrderedMap<>();
		args.add(
				FacadeRequestHandler.CHAIN_KEY, 
				SAMPLE_VALID_CHAIN.stream().collect(joining(",")));
		
		params = new ModifiableSolrParams().add(SAMPLE_KEY, SAMPLE_VALUE);
	}
	
	@Test
	public void cloneResponse() {
		final Object response = new Object();
		final NamedList<Object> responseHeader = new SimpleOrderedMap<>();
	
		when(qresponse.getResponse()).thenReturn(response);
		when(qresponse.getResponseHeader()).thenReturn(responseHeader);
		
		final SolrQueryResponse clone = cut.newFrom(qresponse);
		
		assertNull(clone.getResponse());
		assertSame(responseHeader, clone.getResponseHeader());
	}
	
	@Test
	public void chainIsDefinedOnInit() {
		cut.init(args);
		assertEquals(SAMPLE_VALID_CHAIN, cut.chain);
	}
	
	@Test
	public void emptyChain() {
		final NamedList<Object> args = new SimpleOrderedMap<>();
		cut.init(args);
		
		assertTrue(cut.chain.isEmpty());
	}

	@Test
	public void zeroDocFound() {
		assertEquals(
				0, 
				cut.howManyFound(
						cut.emptyResponse(qrequest, qresponse)));
	}
	
	@Test
	public void zeroDocFoundInCaseOfNoResultContext() {
		assertEquals(0, cut.howManyFound(new SimpleOrderedMap<>()));
	}
	
	@Test
	public void newRequest() {
		final SolrQueryRequest newRequest = cut.newFrom(qrequest, params);
 
		verify(qrequest).getCore();

		assertNotSame(newRequest, qrequest);
		assertNotSame(newRequest.getParams(), params);
		
		assertEquals(params.size(), newRequest.getParams().toNamedList().size());
		assertEquals(
				newRequest.getParams().get("SAMPLE_KEY"), 
				params.get("SAMPLE_KEY"));
	}
	
	@Test
	public void executeQuery() {
		final SolrRequestHandler handler = mock(SolrRequestHandler.class);
		
		final NamedList<?> result = cut.executeQuery(qrequest, qresponse, params, handler);
		
		verify(handler).handleRequest(any(SolrQueryRequest.class), any(SolrQueryResponse.class));
		assertEquals(1, result.size());
	}
	
	@Test
	public void getCore() {
		cut.core(qrequest);
		verify(qrequest).getCore();
	}
	
	@Test
	public void getRequestHandler() {
		final SolrCore core = mock(SolrCore.class);
		
		when(qrequest.getCore()).thenReturn(core);
		when(core.getRequestHandler("/rh1")).thenReturn(rh1);
		when(core.getRequestHandler("/rh2")).thenReturn(rh2);
		when(core.getRequestHandler("/rh3")).thenReturn(rh3);

		assertSame(rh1, cut.requestHandler(qrequest, "/rh1"));
		assertSame(rh2, cut.requestHandler(qrequest, "/rh2"));
		assertSame(rh3, cut.requestHandler(qrequest, "/rh3"));
	}
}
