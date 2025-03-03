/*
 * Copyright 2005-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ldap.core;

import java.util.List;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import org.springframework.LdapDataEntry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.LimitExceededException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.PartialResultException;
import org.springframework.ldap.UncategorizedLdapException;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.odm.core.ObjectDirectoryMapper;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Unit tests for the LdapTemplate class.
 *
 * @author Mattias Hellborg Arthursson
 * @author Ulrik Sandberg
 */
public class LdapTemplateTests {

	private static final String DEFAULT_BASE_STRING = "o=example.com";

	private ContextSource contextSourceMock;

	private DirContext dirContextMock;

	private AttributesMapper attributesMapperMock;

	private NamingEnumeration namingEnumerationMock;

	private Name nameMock;

	private NameClassPairCallbackHandler handlerMock;

	private ContextMapper contextMapperMock;

	private ContextExecutor contextExecutorMock;

	private SearchExecutor searchExecutorMock;

	private LdapTemplate tested;

	private DirContextProcessor dirContextProcessorMock;

	private DirContextOperations dirContextOperationsMock;

	private DirContext authenticatedContextMock;

	private AuthenticatedLdapEntryContextCallback entryContextCallbackMock;

	private ObjectDirectoryMapper odmMock;

	private LdapQuery query;

	private AuthenticatedLdapEntryContextMapper authContextMapperMock;

	@Before
	public void setUp() throws Exception {

		// Setup ContextSource mock
		this.contextSourceMock = mock(ContextSource.class);
		// Setup LdapContext mock
		this.dirContextMock = mock(LdapContext.class);
		// Setup NamingEnumeration mock
		this.namingEnumerationMock = mock(NamingEnumeration.class);
		// Setup Name mock
		this.nameMock = LdapUtils.emptyLdapName();
		// Setup Handler mock
		this.handlerMock = mock(NameClassPairCallbackHandler.class);
		this.contextMapperMock = mock(ContextMapper.class);
		this.attributesMapperMock = mock(AttributesMapper.class);
		this.contextExecutorMock = mock(ContextExecutor.class);
		this.searchExecutorMock = mock(SearchExecutor.class);
		this.dirContextProcessorMock = mock(DirContextProcessor.class);
		this.dirContextOperationsMock = mock(DirContextOperations.class);
		this.authenticatedContextMock = mock(DirContext.class);
		this.entryContextCallbackMock = mock(AuthenticatedLdapEntryContextCallback.class);
		this.odmMock = mock(ObjectDirectoryMapper.class);
		this.query = LdapQueryBuilder.query().base("ou=spring").filter("ou=user");
		this.authContextMapperMock = mock(AuthenticatedLdapEntryContextMapper.class);

		this.tested = new LdapTemplate(this.contextSourceMock);
		this.tested.setObjectDirectoryMapper(this.odmMock);
	}

	private void expectGetReadWriteContext() {
		when(this.contextSourceMock.getReadWriteContext()).thenReturn(this.dirContextMock);
	}

	private void expectGetReadOnlyContext() {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
	}

	@Test
	public void testSearch_CallbackHandler() throws Exception {
		expectGetReadOnlyContext();

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResult(searchControlsOneLevel(), searchResult);

		this.tested.search(this.nameMock, "(ou=somevalue)", 1, true, this.handlerMock);

		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_StringBase_CallbackHandler() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", 1, true, this.handlerMock);

		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_CallbackHandler_Defaults() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResult(controls, searchResult);

		this.tested.search(this.nameMock, "(ou=somevalue)", this.handlerMock);

		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_String_CallbackHandler_Defaults() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", this.handlerMock);

		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_NameNotFoundException() throws Exception {
		expectGetReadOnlyContext();

		final SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		javax.naming.NameNotFoundException ne = new javax.naming.NameNotFoundException("some text");
		when(this.dirContextMock.search(eq(this.nameMock), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenThrow(ne);

		try {
			this.tested.search(this.nameMock, "(ou=somevalue)", this.handlerMock);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_NamingException() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		javax.naming.LimitExceededException ne = new javax.naming.LimitExceededException();
		when(this.dirContextMock.search(eq(this.nameMock), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenThrow(ne);

		try {
			this.tested.search(this.nameMock, "(ou=somevalue)", this.handlerMock);
			fail("LimitExceededException expected");
		}
		catch (LimitExceededException expected) {
			// expected
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_CallbackHandler_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResult(controls, searchResult);

		this.tested.search(this.nameMock, "(ou=somevalue)", controls, this.handlerMock, this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_String_CallbackHandler_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		SearchResult searchResult = new SearchResult("", new Object(), new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.handlerMock,
				this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_String_AttributesMapper_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.attributesMapperMock,
				this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_Name_AttributesMapper_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", controls, this.attributesMapperMock,
				this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_SearchControls_ContextMapper_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.contextMapperMock,
				this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_Name_SearchControls_ContextMapper_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", controls, this.contextMapperMock,
				this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_AttributesMapper_ReturningAttrs() throws Exception {
		expectGetReadOnlyContext();

		String[] attrs = new String[0];
		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
		controls.setReturningObjFlag(false);
		controls.setReturningAttributes(attrs);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", 1, attrs, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_AttributesMapper_ReturningAttrs() throws Exception {
		expectGetReadOnlyContext();

		String[] attrs = new String[0];
		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
		controls.setReturningObjFlag(false);
		controls.setReturningAttributes(attrs);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", 1, attrs, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void verifyThatDefaultSearchControlParametersAreAutomaticallyAppliedInSearch() throws Exception {
		this.tested.setDefaultSearchScope(SearchControls.ONELEVEL_SCOPE);
		this.tested.setDefaultCountLimit(5000);
		this.tested.setDefaultTimeLimit(500);

		expectGetReadOnlyContext();

		SearchControls controls = new SearchControls();
		controls.setReturningObjFlag(false);
		controls.setCountLimit(5000);
		controls.setTimeLimit(500);
		controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_AttributesMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", 1, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_AttributesMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", 1, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_AttributesMapper_Default() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_AttributesMapper_Default() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_ContextMapper() throws Exception {
		expectGetReadOnlyContext();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());
		singleSearchResult(searchControlsOneLevel(), searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", 1, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testFindOne() throws Exception {
		Class<Object> expectedClass = Object.class;

		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.odmMock.filterFor(expectedClass, new EqualsFilter("ou", "somevalue")))
				.thenReturn(new EqualsFilter("ou", "somevalue"));

		DirContextAdapter expectedObject = new DirContextAdapter();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());
		singleSearchResult(searchControlsRecursive(), searchResult);

		Object expectedResult = expectedObject;
		when(this.odmMock.mapFromLdapDataEntry(expectedObject, expectedClass)).thenReturn(expectedResult);

		Object result = this.tested.findOne(query().where("ou").is("somevalue"), expectedClass);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(result).isSameAs(expectedResult);
	}

	@Test
	public void verifyThatFindOneThrowsEmptyResultIfNoResult() throws Exception {
		Class<Object> expectedClass = Object.class;

		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.odmMock.filterFor(expectedClass, new EqualsFilter("ou", "somevalue")))
				.thenReturn(new EqualsFilter("ou", "somevalue"));

		noSearchResults(searchControlsRecursive());

		try {
			this.tested.findOne(query().where("ou").is("somevalue"), expectedClass);
			fail("EmptyResultDataAccessException expected");
		}
		catch (EmptyResultDataAccessException expected) {
			assertThat(true).isTrue();
		}

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
		verify(this.odmMock, never()).mapFromLdapDataEntry(any(LdapDataEntry.class), any(Class.class));
	}

	@Test
	public void verifyThatFindOneThrowsIncorrectResultSizeDataAccessExceptionWhenMoreResults() throws Exception {
		Class<Object> expectedClass = Object.class;

		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.odmMock.filterFor(expectedClass, new EqualsFilter("ou", "somevalue")))
				.thenReturn(new EqualsFilter("ou", "somevalue"));

		DirContextAdapter expectedObject = new DirContextAdapter();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		setupSearchResults(searchControlsRecursive(), new SearchResult[] { searchResult, searchResult });

		Object expectedResult = expectedObject;
		when(this.odmMock.mapFromLdapDataEntry(expectedObject, expectedClass)).thenReturn(expectedResult,
				expectedResult);

		try {
			this.tested.findOne(query().where("ou").is("somevalue"), expectedClass);
			fail("EmptyResultDataAccessException expected");
		}
		catch (IncorrectResultSizeDataAccessException expected) {
			assertThat(true).isTrue();
		}

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void findWhenSearchControlsReturningAttributesSpecifiedThenOverridesOdmReturningAttributes()
			throws Exception {
		Class<Object> expectedClass = Object.class;

		Filter filter = new EqualsFilter("ou", "somevalue");
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.odmMock.filterFor(any(Class.class), any(Filter.class))).thenReturn(filter);
		SearchControls controls = new SearchControls();
		controls.setReturningAttributes(new String[] { "attribute" });
		DirContextAdapter expectedObject = new DirContextAdapter();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());
		setupSearchResults(controls, searchResult);
		Object expectedResult = expectedObject;
		when(this.odmMock.mapFromLdapDataEntry(expectedObject, expectedClass)).thenReturn(expectedResult,
				expectedResult);

		List<Object> results = this.tested.find(this.nameMock, filter, controls, expectedClass);
		assertThat(results).hasSize(1);
		verify(this.odmMock, never()).manageClass(any(Class.class));

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void findWhenSearchControlsReturningAttributesUnspecifiedThenOdmReturningAttributesOverrides()
			throws Exception {
		Class<Object> expectedClass = Object.class;
		String[] expectedReturningAttributes = new String[] { "odmattribute" };
		SearchControls expectedControls = new SearchControls();
		expectedControls.setReturningObjFlag(true);
		expectedControls.setReturningAttributes(expectedReturningAttributes);

		Filter filter = new EqualsFilter("ou", "somevalue");
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.odmMock.filterFor(eq(expectedClass), any(Filter.class))).thenReturn(filter);
		when(this.odmMock.manageClass(eq(expectedClass))).thenReturn(expectedReturningAttributes);
		SearchControls controls = new SearchControls();
		DirContextAdapter expectedObject = new DirContextAdapter();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());
		setupSearchResults(expectedControls, searchResult);
		Object expectedResult = expectedObject;
		when(this.odmMock.mapFromLdapDataEntry(expectedObject, expectedClass)).thenReturn(expectedResult,
				expectedResult);

		List<Object> results = this.tested.find(this.nameMock, filter, controls, expectedClass);
		assertThat(results).hasSize(1);
		verify(this.odmMock).manageClass(eq(expectedClass));

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_ContextMapper_ReturningAttrs() throws Exception {
		expectGetReadOnlyContext();

		String[] attrs = new String[0];

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningAttributes(attrs);

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", 1, attrs, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_ContextMapper_ReturningAttrs() throws Exception {
		expectGetReadOnlyContext();

		String[] attrs = new String[0];

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningAttributes(attrs);

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", 1, attrs, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_ContextMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", 1, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_ContextMapper_Default() throws Exception {
		expectGetReadOnlyContext();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(searchControlsRecursive(), searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_ContextMapper_Default() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_SearchControls_ContextMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_SearchControls_ContextMapper_ReturningObjFlagNotSet() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

		SearchControls expectedControls = new SearchControls();
		expectedControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		expectedControls.setReturningObjFlag(true);

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResultWithStringBase(expectedControls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_Name_SearchControls_ContextMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(controls, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", controls, this.contextMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_String_SearchControls_AttributesMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResultWithStringBase(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(DEFAULT_BASE_STRING, "(ou=somevalue)", controls, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testSearch_Name_SearchControls_AttributesMapper() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsOneLevel();
		controls.setReturningObjFlag(false);

		BasicAttributes expectedAttributes = new BasicAttributes();
		SearchResult searchResult = new SearchResult("", null, expectedAttributes);

		singleSearchResult(controls, searchResult);

		Object expectedResult = new Object();
		when(this.attributesMapperMock.mapFromAttributes(expectedAttributes)).thenReturn(expectedResult);

		List list = this.tested.search(this.nameMock, "(ou=somevalue)", controls, this.attributesMapperMock);

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();

		assertThat(list).isNotNull();
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isSameAs(expectedResult);
	}

	@Test
	public void testModifyAttributes() throws Exception {
		expectGetReadWriteContext();

		ModificationItem[] mods = new ModificationItem[0];

		this.tested.modifyAttributes(this.nameMock, mods);

		verify(this.dirContextMock).modifyAttributes(this.nameMock, mods);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testModifyAttributes_String() throws Exception {
		expectGetReadWriteContext();

		ModificationItem[] mods = new ModificationItem[0];

		this.tested.modifyAttributes(DEFAULT_BASE_STRING, mods);

		verify(this.dirContextMock).modifyAttributes(DEFAULT_BASE_STRING, mods);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testModifyAttributes_NamingException() throws Exception {
		expectGetReadWriteContext();

		ModificationItem[] mods = new ModificationItem[0];

		javax.naming.LimitExceededException ne = new javax.naming.LimitExceededException();
		doThrow(ne).when(this.dirContextMock).modifyAttributes(this.nameMock, mods);

		try {
			this.tested.modifyAttributes(this.nameMock, mods);
			fail("LimitExceededException expected");
		}
		catch (LimitExceededException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testBind() throws Exception {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		BasicAttributes expectedAttributes = new BasicAttributes();

		this.tested.bind(this.nameMock, expectedObject, expectedAttributes);

		verify(this.dirContextMock).bind(this.nameMock, expectedObject, expectedAttributes);
		verify(this.dirContextMock).close();

	}

	@Test
	public void testBind_String() throws Exception {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		BasicAttributes expectedAttributes = new BasicAttributes();

		this.tested.bind(DEFAULT_BASE_STRING, expectedObject, expectedAttributes);

		verify(this.dirContextMock).bind(DEFAULT_BASE_STRING, expectedObject, expectedAttributes);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testBind_NamingException() throws Exception {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		BasicAttributes expectedAttributes = new BasicAttributes();
		javax.naming.NameNotFoundException ne = new javax.naming.NameNotFoundException();
		doThrow(ne).when(this.dirContextMock).bind(this.nameMock, expectedObject, expectedAttributes);

		try {
			this.tested.bind(this.nameMock, expectedObject, expectedAttributes);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testBindWithContext() throws Exception {
		expectGetReadWriteContext();

		when(this.dirContextOperationsMock.getDn()).thenReturn(this.nameMock);
		when(this.dirContextOperationsMock.isUpdateMode()).thenReturn(false);

		this.tested.bind(this.dirContextOperationsMock);

		verify(this.dirContextMock).bind(this.nameMock, this.dirContextOperationsMock, null);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testCreateWithIdSpecified() throws NamingException {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		LdapName expectedName = LdapUtils.newLdapName("ou=someOu");
		when(this.odmMock.getId(expectedObject)).thenReturn(expectedName);

		ArgumentCaptor<DirContextAdapter> ctxCaptor = ArgumentCaptor.forClass(DirContextAdapter.class);
		doNothing().when(this.odmMock).mapToLdapDataEntry(eq(expectedObject), ctxCaptor.capture());

		this.tested.create(expectedObject);

		verify(this.odmMock, never()).setId(expectedObject, expectedName);
		verify(this.dirContextMock).bind(expectedName, ctxCaptor.getValue(), null);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testCreateWithCalculatedId() throws NamingException {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		LdapName expectedName = LdapUtils.newLdapName("ou=someOu");
		when(this.odmMock.getId(expectedObject)).thenReturn(null);
		when(this.odmMock.getCalculatedId(expectedObject)).thenReturn(expectedName);

		ArgumentCaptor<DirContextAdapter> ctxCaptor = ArgumentCaptor.forClass(DirContextAdapter.class);
		doNothing().when(this.odmMock).mapToLdapDataEntry(eq(expectedObject), ctxCaptor.capture());

		this.tested.create(expectedObject);

		verify(this.odmMock).setId(expectedObject, expectedName);
		verify(this.dirContextMock).bind(expectedName, ctxCaptor.getValue(), null);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testCreateWithNoIdAvailableThrows() throws NamingException {
		Object expectedObject = new Object();
		when(this.odmMock.getId(expectedObject)).thenReturn(null);
		when(this.odmMock.getCalculatedId(expectedObject)).thenReturn(null);

		try {
			this.tested.create(expectedObject);
			fail("IllegalArgumentException expected");
		}
		catch (IllegalArgumentException expected) {
			assertThat(true).isTrue();
		}
	}

	@Test
	public void testUpdateWithIdSpecified() throws NamingException {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.contextSourceMock.getReadWriteContext()).thenReturn(this.dirContextMock);
		LdapName expectedName = LdapUtils.newLdapName("ou=someOu");

		ModificationItem[] expectedModificationItems = new ModificationItem[0];
		DirContextOperations ctxMock = mock(DirContextOperations.class);
		when(ctxMock.getDn()).thenReturn(expectedName);
		when(ctxMock.isUpdateMode()).thenReturn(true);
		when(ctxMock.getModificationItems()).thenReturn(expectedModificationItems);

		Object expectedObject = new Object();
		when(this.odmMock.getId(expectedObject)).thenReturn(expectedName);
		when(this.odmMock.getCalculatedId(expectedObject)).thenReturn(null);

		when(this.dirContextMock.lookup(expectedName)).thenReturn(ctxMock);

		this.tested.update(expectedObject);

		verify(this.odmMock, never()).setId(expectedObject, expectedName);
		verify(this.odmMock).mapToLdapDataEntry(expectedObject, ctxMock);
		verify(this.dirContextMock).modifyAttributes(expectedName, expectedModificationItems);

		verify(this.dirContextMock, times(2)).close();
	}

	@Test
	public void testUpdateWithIdCalculated() throws NamingException {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);
		when(this.contextSourceMock.getReadWriteContext()).thenReturn(this.dirContextMock);
		LdapName expectedName = LdapUtils.newLdapName("ou=someOu");

		ModificationItem[] expectedModificationItems = new ModificationItem[0];
		DirContextOperations ctxMock = mock(DirContextOperations.class);
		when(ctxMock.getDn()).thenReturn(expectedName);
		when(ctxMock.isUpdateMode()).thenReturn(true);
		when(ctxMock.getModificationItems()).thenReturn(expectedModificationItems);

		Object expectedObject = new Object();
		when(this.odmMock.getId(expectedObject)).thenReturn(null);
		when(this.odmMock.getCalculatedId(expectedObject)).thenReturn(expectedName);

		when(this.dirContextMock.lookup(expectedName)).thenReturn(ctxMock);

		this.tested.update(expectedObject);

		verify(this.odmMock).setId(expectedObject, expectedName);
		verify(this.odmMock).mapToLdapDataEntry(expectedObject, ctxMock);
		verify(this.dirContextMock).modifyAttributes(expectedName, expectedModificationItems);

		verify(this.dirContextMock, times(2)).close();
	}

	@Test
	public void testUpdateWithIdChanged() throws NamingException {
		Object expectedObject = new Object();

		when(this.contextSourceMock.getReadWriteContext()).thenReturn(this.dirContextMock, this.dirContextMock);
		LdapName expectedOriginalName = LdapUtils.newLdapName("ou=someOu");
		LdapName expectedNewName = LdapUtils.newLdapName("ou=someOtherOu");

		ArgumentCaptor<DirContextAdapter> ctxCaptor = ArgumentCaptor.forClass(DirContextAdapter.class);
		doNothing().when(this.odmMock).mapToLdapDataEntry(eq(expectedObject), ctxCaptor.capture());

		when(this.odmMock.getId(expectedObject)).thenReturn(expectedOriginalName);
		when(this.odmMock.getCalculatedId(expectedObject)).thenReturn(expectedNewName);

		this.tested.update(expectedObject);

		verify(this.odmMock).setId(expectedObject, expectedNewName);
		verify(this.dirContextMock).unbind(expectedOriginalName);
		verify(this.dirContextMock).bind(expectedNewName, ctxCaptor.getValue(), null);
		verify(this.dirContextMock, times(2)).close();
	}

	@Test
	public void testUnbind() throws Exception {
		expectGetReadWriteContext();

		this.tested.unbind(this.nameMock);

		verify(this.dirContextMock).unbind(this.nameMock);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testUnbind_String() throws Exception {
		expectGetReadWriteContext();

		this.tested.unbind(DEFAULT_BASE_STRING);

		verify(this.dirContextMock).unbind(DEFAULT_BASE_STRING);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testRebindWithContext() throws Exception {
		expectGetReadWriteContext();

		when(this.dirContextOperationsMock.getDn()).thenReturn(this.nameMock);
		when(this.dirContextOperationsMock.isUpdateMode()).thenReturn(false);

		this.tested.rebind(this.dirContextOperationsMock);

		verify(this.dirContextMock).rebind(this.nameMock, this.dirContextOperationsMock, null);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testUnbindRecursive() throws Exception {
		expectGetReadWriteContext();

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, false, false);
		Binding binding = new Binding("cn=Some name", null);
		when(this.namingEnumerationMock.next()).thenReturn(binding);

		LdapName listDn = LdapUtils.newLdapName(DEFAULT_BASE_STRING);
		when(this.dirContextMock.listBindings(listDn)).thenReturn(this.namingEnumerationMock);
		LdapName subListDn = LdapUtils.newLdapName("cn=Some name, o=example.com");
		when(this.dirContextMock.listBindings(subListDn)).thenReturn(this.namingEnumerationMock);

		this.tested.unbind(new CompositeName(DEFAULT_BASE_STRING), true);

		verify(this.dirContextMock).unbind(subListDn);
		verify(this.dirContextMock).unbind(listDn);
		verify(this.namingEnumerationMock, times(2)).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testUnbindRecursive_String() throws Exception {
		expectGetReadWriteContext();

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, false, false);
		Binding binding = new Binding("cn=Some name", null);
		when(this.namingEnumerationMock.next()).thenReturn(binding);

		LdapName listDn = LdapUtils.newLdapName(DEFAULT_BASE_STRING);
		when(this.dirContextMock.listBindings(listDn)).thenReturn(this.namingEnumerationMock);
		LdapName subListDn = LdapUtils.newLdapName("cn=Some name, o=example.com");
		when(this.dirContextMock.listBindings(subListDn)).thenReturn(this.namingEnumerationMock);

		this.tested.unbind(DEFAULT_BASE_STRING, true);

		verify(this.dirContextMock).unbind(subListDn);
		verify(this.dirContextMock).unbind(listDn);
		verify(this.namingEnumerationMock, times(2)).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testRebind() throws Exception {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		BasicAttributes expectedAttributes = new BasicAttributes();

		this.tested.rebind(this.nameMock, expectedObject, expectedAttributes);

		verify(this.dirContextMock).rebind(this.nameMock, expectedObject, expectedAttributes);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testRebind_String() throws Exception {
		expectGetReadWriteContext();

		Object expectedObject = new Object();
		BasicAttributes expectedAttributes = new BasicAttributes();

		this.tested.rebind(DEFAULT_BASE_STRING, expectedObject, expectedAttributes);

		verify(this.dirContextMock).rebind(DEFAULT_BASE_STRING, expectedObject, expectedAttributes);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testUnbind_NamingException() throws Exception {
		expectGetReadWriteContext();

		javax.naming.NameNotFoundException ne = new javax.naming.NameNotFoundException();
		doThrow(ne).when(this.dirContextMock).unbind(this.nameMock);

		try {
			this.tested.unbind(this.nameMock);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testExecuteReadOnly() throws Exception {
		expectGetReadOnlyContext();

		Object object = new Object();
		when(this.contextExecutorMock.executeWithContext(this.dirContextMock)).thenReturn(object);

		Object result = this.tested.executeReadOnly(this.contextExecutorMock);

		verify(this.dirContextMock).close();

		assertThat(result).isSameAs(object);
	}

	@Test
	public void testExecuteReadOnly_NamingException() throws Exception {
		expectGetReadOnlyContext();

		javax.naming.NameNotFoundException ne = new javax.naming.NameNotFoundException();
		when(this.contextExecutorMock.executeWithContext(this.dirContextMock)).thenThrow(ne);

		try {
			this.tested.executeReadOnly(this.contextExecutorMock);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testExecuteReadWrite() throws Exception {
		expectGetReadWriteContext();

		Object object = new Object();
		when(this.contextExecutorMock.executeWithContext(this.dirContextMock)).thenReturn(object);

		Object result = this.tested.executeReadWrite(this.contextExecutorMock);

		verify(this.dirContextMock).close();

		assertThat(result).isSameAs(object);
	}

	@Test
	public void testExecuteReadWrite_NamingException() throws Exception {
		expectGetReadWriteContext();

		javax.naming.NameNotFoundException ne = new javax.naming.NameNotFoundException();
		when(this.contextExecutorMock.executeWithContext(this.dirContextMock)).thenThrow(ne);

		try {
			this.tested.executeReadWrite(this.contextExecutorMock);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch_DirContextProcessor() throws Exception {
		expectGetReadOnlyContext();

		SearchResult searchResult = new SearchResult(null, null, null);

		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, false);
		when(this.namingEnumerationMock.next()).thenReturn(searchResult);

		this.tested.search(this.searchExecutorMock, this.handlerMock, this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch_DirContextProcessor_NamingException() throws Exception {
		expectGetReadOnlyContext();

		javax.naming.LimitExceededException ne = new javax.naming.LimitExceededException();
		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenThrow(ne);

		try {
			this.tested.search(this.searchExecutorMock, this.handlerMock, this.dirContextProcessorMock);
			fail("LimitExceededException expected");
		}
		catch (LimitExceededException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch() throws Exception {
		expectGetReadOnlyContext();

		SearchResult searchResult = new SearchResult(null, null, null);

		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, false);
		when(this.namingEnumerationMock.next()).thenReturn(searchResult);

		this.tested.search(this.searchExecutorMock, this.handlerMock);

		verify(this.handlerMock).handleNameClassPair(searchResult);
		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch_NamingException() throws Exception {
		expectGetReadOnlyContext();

		javax.naming.LimitExceededException ne = new javax.naming.LimitExceededException();
		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenThrow(ne);

		try {
			this.tested.search(this.searchExecutorMock, this.handlerMock);
			fail("LimitExceededException expected");
		}
		catch (LimitExceededException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch_NamingException_NamingEnumeration() throws Exception {
		expectGetReadOnlyContext();

		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenReturn(this.namingEnumerationMock);

		javax.naming.LimitExceededException ne = new javax.naming.LimitExceededException();
		when(this.namingEnumerationMock.hasMore()).thenThrow(ne);

		try {
			this.tested.search(this.searchExecutorMock, this.handlerMock);
			fail("LimitExceededException expected");
		}
		catch (LimitExceededException expected) {
			assertThat(true).isTrue();
		}

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testDoSearch_NameNotFoundException() throws Exception {
		expectGetReadOnlyContext();

		when(this.searchExecutorMock.executeSearch(this.dirContextMock))
				.thenThrow(new javax.naming.NameNotFoundException());

		try {
			this.tested.search(this.searchExecutorMock, this.handlerMock);
			fail("NameNotFoundException expected");
		}
		catch (NameNotFoundException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_PartialResult_IgnoreNotSet() throws Exception {
		expectGetReadOnlyContext();

		javax.naming.PartialResultException ex = new javax.naming.PartialResultException();
		when(this.searchExecutorMock.executeSearch(this.dirContextMock)).thenThrow(ex);

		try {
			this.tested.search(this.searchExecutorMock, this.handlerMock, this.dirContextProcessorMock);
			fail("PartialResultException expected");
		}
		catch (PartialResultException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearch_PartialResult_IgnoreSet() throws Exception {
		this.tested.setIgnorePartialResultException(true);

		expectGetReadOnlyContext();

		when(this.searchExecutorMock.executeSearch(this.dirContextMock))
				.thenThrow(new javax.naming.PartialResultException());

		this.tested.search(this.searchExecutorMock, this.handlerMock, this.dirContextProcessorMock);

		verify(this.dirContextProcessorMock).preProcess(this.dirContextMock);
		verify(this.dirContextProcessorMock).postProcess(this.dirContextMock);
		verify(this.dirContextMock).close();
	}

	@Test
	public void testLookupContextWithName() {
		final DirContextAdapter expectedResult = new DirContextAdapter();

		final LdapName expectedName = LdapUtils.emptyLdapName();
		LdapTemplate tested = new LdapTemplate() {
			public Object lookup(Name dn) {
				assertThat(dn).isSameAs(dn);
				return expectedResult;
			}
		};

		DirContextOperations result = tested.lookupContext(expectedName);
		assertThat(result).isSameAs(expectedResult);

	}

	@Test
	public void testLookupContextWithString() {
		final DirContextAdapter expectedResult = new DirContextAdapter();
		final String expectedName = "cn=John Doe";

		LdapTemplate tested = new LdapTemplate() {
			public Object lookup(String dn) {
				assertThat(dn).isSameAs(expectedName);
				return expectedResult;
			}
		};

		DirContextOperations result = tested.lookupContext(expectedName);
		assertThat(result).isSameAs(expectedResult);
	}

	@Test
	public void testModifyAttributesWithDirContextOperations() throws Exception {
		final ModificationItem[] expectedModifications = new ModificationItem[0];

		final LdapName epectedDn = LdapUtils.emptyLdapName();
		when(this.dirContextOperationsMock.getDn()).thenReturn(epectedDn);
		when(this.dirContextOperationsMock.isUpdateMode()).thenReturn(true);
		when(this.dirContextOperationsMock.getModificationItems()).thenReturn(expectedModifications);

		LdapTemplate tested = new LdapTemplate() {
			public void modifyAttributes(Name dn, ModificationItem[] mods) {
				assertThat(dn).isSameAs(epectedDn);
				assertThat(mods).isSameAs(expectedModifications);
			}
		};

		tested.modifyAttributes(this.dirContextOperationsMock);
	}

	@Test
	public void testModifyAttributesWithDirContextOperationsNotInitializedDn() throws Exception {

		when(this.dirContextOperationsMock.getDn()).thenReturn(LdapUtils.emptyLdapName());
		when(this.dirContextOperationsMock.isUpdateMode()).thenReturn(false);

		LdapTemplate tested = new LdapTemplate() {
			public void modifyAttributes(Name dn, ModificationItem[] mods) {
				fail("The call to the base modifyAttributes should not have occured.");
			}
		};

		try {
			tested.modifyAttributes(this.dirContextOperationsMock);
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException expected) {
			assertThat(true).isTrue();
		}
	}

	@Test
	public void testModifyAttributesWithDirContextOperationsNotInitializedInUpdateMode() throws Exception {
		when(this.dirContextOperationsMock.getDn()).thenReturn(null);

		LdapTemplate tested = new LdapTemplate() {
			public void modifyAttributes(Name dn, ModificationItem[] mods) {
				fail("The call to the base modifyAttributes should not have occured.");
			}
		};

		try {
			tested.modifyAttributes(this.dirContextOperationsMock);
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException expected) {
			assertThat(true).isTrue();
		}
	}

	@Test
	public void testSearchForObject() throws Exception {
		expectGetReadOnlyContext();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(searchControlsRecursive(), searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		Object result = this.tested.searchForObject(this.nameMock, "(ou=somevalue)", this.contextMapperMock);

		verify(this.dirContextMock).close();

		assertThat(result).isNotNull();
		assertThat(result).isSameAs(expectedResult);
	}

	@Test
	public void testSearchForObjectWithMultipleResults() throws Exception {
		expectGetReadOnlyContext();

		SearchControls controls = searchControlsRecursive();

		Object expectedObject = new Object();
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		when(this.dirContextMock.search(eq(this.nameMock), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, true, false);
		when(this.namingEnumerationMock.next()).thenReturn(searchResult, searchResult);

		Object expectedResult = expectedObject;
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);
		when(this.contextMapperMock.mapFromContext(expectedObject)).thenReturn(expectedResult);

		try {
			this.tested.searchForObject(this.nameMock, "(ou=somevalue)", this.contextMapperMock);
			fail("IncorrectResultSizeDataAccessException expected");
		}
		catch (IncorrectResultSizeDataAccessException expected) {
			assertThat(true).isTrue();
		}

		verify(this.namingEnumerationMock).close();
		verify(this.dirContextMock).close();
	}

	@Test
	public void testSearchForObjectWithNoResults() throws Exception {
		expectGetReadOnlyContext();

		noSearchResults(searchControlsRecursive());

		try {
			this.tested.searchForObject(this.nameMock, "(ou=somevalue)", this.contextMapperMock);
			fail("EmptyResultDataAccessException expected");
		}
		catch (EmptyResultDataAccessException expected) {
			assertThat(true).isTrue();
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testAuthenticateWithSingleUserFoundShouldBeSuccessful() throws Exception {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		Object expectedObject = new DirContextAdapter(new BasicAttributes(), LdapUtils.newLdapName("cn=john doe"),
				LdapUtils.newLdapName("dc=jayway, dc=se"));
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(searchControlsRecursive(), searchResult);

		when(this.contextSourceMock.getContext("cn=john doe,dc=jayway,dc=se", "password"))
				.thenReturn(this.authenticatedContextMock);
		this.entryContextCallbackMock.executeWithContext(this.authenticatedContextMock, new LdapEntryIdentification(
				LdapUtils.newLdapName("cn=john doe,dc=jayway,dc=se"), LdapUtils.newLdapName("cn=john doe")));

		boolean result = this.tested.authenticate(this.nameMock, "(ou=somevalue)", "password",
				this.entryContextCallbackMock);

		verify(this.authenticatedContextMock).close();
		verify(this.dirContextMock).close();

		assertThat(result).isTrue();
	}

	@Test
	public void testAuthenticateWithTwoUsersFoundShouldThrowException() throws Exception {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		Object expectedObject = new DirContextAdapter(new BasicAttributes(), LdapUtils.newLdapName("cn=john doe"),
				LdapUtils.newLdapName("dc=jayway, dc=se"));
		SearchResult searchResult1 = new SearchResult("", expectedObject, new BasicAttributes());
		SearchResult searchResult2 = new SearchResult("", expectedObject, new BasicAttributes());

		setupSearchResults(searchControlsRecursive(), new SearchResult[] { searchResult1, searchResult2 });

		try {
			this.tested.authenticate(this.nameMock, "(ou=somevalue)", "password", this.entryContextCallbackMock);
			fail("IncorrectResultSizeDataAccessException expected");
		}
		catch (IncorrectResultSizeDataAccessException expected) {
			// expected
		}

		verify(this.dirContextMock).close();
	}

	@Test
	public void testAuthenticateWhenNoUserWasFoundShouldFail() throws Exception {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		noSearchResults(searchControlsRecursive());

		boolean result = this.tested.authenticate(this.nameMock, "(ou=somevalue)", "password",
				this.entryContextCallbackMock);

		verify(this.dirContextMock).close();

		assertThat(result).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testAuthenticateQueryPasswordMapperWhenNoUserWasFoundShouldThrowEmptyResult() throws Exception {

		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		when(this.dirContextMock.search(any(Name.class), any(String.class), any(SearchControls.class)))
				.thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(false);

		try {
			this.tested.authenticate(this.query, "", this.authContextMapperMock);
			fail("Expected Exception");
		}
		catch (EmptyResultDataAccessException success) {
		}
		verify(this.dirContextMock).close();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testAuthenticateQueryPasswordWhenNoUserWasFoundShouldThrowEmptyResult() throws Exception {

		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		when(this.dirContextMock.search(any(Name.class), any(String.class), any(SearchControls.class)))
				.thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(false);

		try {
			this.tested.authenticate(this.query, "");
			fail("Expected Exception");
		}
		catch (EmptyResultDataAccessException success) {
		}
		verify(this.dirContextMock).close();
	}

	@Test
	public void testAuthenticateWithFailedAuthenticationShouldFail() throws Exception {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		Object expectedObject = new DirContextAdapter(new BasicAttributes(), LdapUtils.newLdapName("cn=john doe"),
				LdapUtils.newLdapName("dc=jayway, dc=se"));
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(searchControlsRecursive(), searchResult);

		when(this.contextSourceMock.getContext("cn=john doe,dc=jayway,dc=se", "password"))
				.thenThrow(new UncategorizedLdapException("Authentication failed"));

		boolean result = this.tested.authenticate(this.nameMock, "(ou=somevalue)", "password",
				this.entryContextCallbackMock);

		verify(this.dirContextMock).close();

		assertThat(result).isFalse();
	}

	@Test
	public void testAuthenticateWithErrorInCallbackShouldFail() throws Exception {
		when(this.contextSourceMock.getReadOnlyContext()).thenReturn(this.dirContextMock);

		Object expectedObject = new DirContextAdapter(new BasicAttributes(), LdapUtils.newLdapName("cn=john doe"),
				LdapUtils.newLdapName("dc=jayway, dc=se"));
		SearchResult searchResult = new SearchResult("", expectedObject, new BasicAttributes());

		singleSearchResult(searchControlsRecursive(), searchResult);

		when(this.contextSourceMock.getContext("cn=john doe,dc=jayway,dc=se", "password"))
				.thenReturn(this.authenticatedContextMock);
		doThrow(new UncategorizedLdapException("Authentication failed")).when(this.entryContextCallbackMock)
				.executeWithContext(this.authenticatedContextMock, new LdapEntryIdentification(
						LdapUtils.newLdapName("cn=john doe,dc=jayway,dc=se"), LdapUtils.newLdapName("cn=john doe")));

		boolean result = this.tested.authenticate(this.nameMock, "(ou=somevalue)", "password",
				this.entryContextCallbackMock);

		verify(this.authenticatedContextMock).close();
		verify(this.dirContextMock).close();

		assertThat(result).isFalse();
	}

	private void noSearchResults(SearchControls controls) throws Exception {
		when(this.dirContextMock.search(eq(this.nameMock), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(false);
	}

	private void singleSearchResult(SearchControls controls, SearchResult searchResult) throws Exception {
		setupSearchResults(controls, new SearchResult[] { searchResult });
	}

	private void setupSearchResults(SearchControls controls, SearchResult... searchResults) throws Exception {
		when(this.dirContextMock.search(eq(this.nameMock), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenReturn(this.namingEnumerationMock);

		if (searchResults.length == 1) {
			when(this.namingEnumerationMock.hasMore()).thenReturn(true, false);
			when(this.namingEnumerationMock.next()).thenReturn(searchResults[0]);
		}
		else if (searchResults.length == 2) {
			when(this.namingEnumerationMock.hasMore()).thenReturn(true, true, false);
			when(this.namingEnumerationMock.next()).thenReturn(searchResults[0], searchResults[1]);
		}
		else {
			throw new IllegalArgumentException("Cannot handle " + searchResults.length + " search results");
		}
	}

	private void singleSearchResultWithStringBase(SearchControls controls, SearchResult searchResult) throws Exception {
		when(this.dirContextMock.search(eq(DEFAULT_BASE_STRING), eq("(ou=somevalue)"),
				argThat(new SearchControlsMatcher(controls)))).thenReturn(this.namingEnumerationMock);

		when(this.namingEnumerationMock.hasMore()).thenReturn(true, false);
		when(this.namingEnumerationMock.next()).thenReturn(searchResult);
	}

	private SearchControls searchControlsRecursive() {
		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		controls.setReturningObjFlag(true);
		return controls;
	}

	private SearchControls searchControlsOneLevel() {
		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
		controls.setReturningObjFlag(true);
		return controls;
	}

	private static class SearchControlsMatcher implements ArgumentMatcher<SearchControls> {

		private final SearchControls controls;

		public SearchControlsMatcher(SearchControls controls) {
			this.controls = controls;
		}

		@Override
		public boolean matches(SearchControls item) {
			if (item instanceof SearchControls) {
				SearchControls s1 = item;

				return this.controls.getSearchScope() == s1.getSearchScope()
						&& this.controls.getReturningObjFlag() == s1.getReturningObjFlag()
						&& this.controls.getDerefLinkFlag() == s1.getDerefLinkFlag()
						&& this.controls.getCountLimit() == s1.getCountLimit()
						&& this.controls.getTimeLimit() == s1.getTimeLimit()
						&& this.controls.getReturningAttributes() == s1.getReturningAttributes();
			}
			else {
				throw new IllegalArgumentException();
			}
		}

	}

}
