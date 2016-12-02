package org.gx.labs.crh;

import static java.util.Arrays.stream;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocSlice;
import org.apache.solr.util.RTimerTree;
import org.eclipse.jetty.http.HttpParser.RequestHandler;

/**
 * A {@link SolrRequestHandler} that subsequently invokes several children {@link SolrRequestHandler}s.
 * 
 * @author agazzarini
 * @since 1.0
 */
public class FacadeRequestHandler extends RequestHandlerBase {
	private final static DocList EMPTY_DOCLIST = new DocSlice(0, 0, new int[0], new float[0], 0, 0f);
	
	final static String RESPONSE_KEY = "response"; // If only SolrQueryResponse.RESPONSE_KEY would be public ;)
	final static String RESPONSE_HEADER_KEY = "responseHeader"; // If only SolrQueryResponse.RESPONSE_HEADER_KEY would be public ;)
	
	private String [] chain;
	
	@Override
	@SuppressWarnings("rawtypes")
	public void init(final NamedList args) {
		chain = SolrParams.toSolrParams(args).get("chain").split(",");
	}
	
	@Override
	public void handleRequestBody(
			final SolrQueryRequest request, 
			final SolrQueryResponse response) throws Exception {
		final SolrParams params = request.getParams();

		response.setAllValues(
			stream(chain)
				.map(refName -> { return requestHandler(request, refName); })
				.filter(SearchHandler.class::isInstance) 
				.map(handler -> { return executeQuery(request, response, params, handler); })
				.filter(qresponse -> howManyFound(qresponse) > 0)
				.findFirst()
				.orElse(emptyResponse(request, response)));
	}
	
	/**
	 * Returns the total count of matches associated with the given query response.
	 * 
	 * @param qresponse the "response" portion of the {@link QueryResponse}.
	 * @return the total count of matches associated with the given query response.
	 */
	int howManyFound(final NamedList<?> qresponse) {
		return ((ResultContext)qresponse.get(RESPONSE_KEY)).getDocList().size();
	}
	
	/**
	 * Returns a Null Object response, indicating no handler produced a match.
	 * 
	 * @param request the current {@link SolrQueryRequest}.
	 * @param response the current {@link SolrQueryResponse}.
	 * @return a Null Object response, indicating no handler produced a match.
	 */
	NamedList<Object> emptyResponse(final SolrQueryRequest request, final SolrQueryResponse response) {
		final SimpleOrderedMap<Object> empty = new SimpleOrderedMap<>();
		empty.add(RESPONSE_KEY, 
				new BasicResultContext(
					EMPTY_DOCLIST, 
					response.getReturnFields(),
					request.getSearcher(),
					null,
					request));
		
		empty.add(RESPONSE_HEADER_KEY, new SimpleOrderedMap<>());
		return empty;
	}
	
	/**
	 * Executes the given handler (query) logic.
	 * @param request the current {@link SolrQueryRequest}.
	 * @param response the current {@link SolrQueryResponse}.
	 * @param params the request parameters.
	 * @param handler the executor handler.
	 * @return the query response, that is, the result of the handler's query execution.
	 */
	@SuppressWarnings("unchecked")
	NamedList<Object> executeQuery(
			final SolrQueryRequest request, 
			final SolrQueryResponse response, 
			final SolrParams params, 
			final SolrRequestHandler handler) {
		final SolrQueryResponse scopedResponse = clone(response);
		handler.handleRequest(
				new SolrQueryRequestBase(
						request.getCore(), 
						new ModifiableSolrParams(params), 
						new RTimerTree()) {}, 
				scopedResponse); 
		return scopedResponse.getValues();	
	}
	
	/**
	 * Clones a given response.
	 * 
	 * @param response the original {@link SolrQueryResponse}.
	 * @return a clone of the incoming response.
	 */
	public SolrQueryResponse clone(final SolrQueryResponse response) {
		final SolrQueryResponse clone = new SolrQueryResponse();
		clone.addResponseHeader(response.getResponseHeader());
		return clone;
	}
	
	/**
	 * Returns the {@link RequestHandler} associated with the given name.
	 * 
	 * @param request the current {@link SolrQueryRequest}.
	 * @param name the name of the requested {@link RequestHandler}.
	 * @return the {@link RequestHandler} associated with the given name.
	 */
	SolrRequestHandler requestHandler(final SolrQueryRequest request, final String name) {
		return core(request).getRequestHandler(name);
	}
	
	/**
	 * Returns the {@link SolrCore} associated with the given request.
	 * 
	 * @param request the current {@link SolrQueryRequest}.
	 * @return the {@link SolrCore} associated with the given request.
	 */
	SolrCore core(final SolrQueryRequest request) {
		return request.getCore();
	}
	
	@Override
	public String getDescription() {
		return "A RequestHandler that wraps two or more request handlers in chain.";
	}
}