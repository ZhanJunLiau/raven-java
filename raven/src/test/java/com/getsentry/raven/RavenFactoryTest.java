package com.getsentry.raven;

import mockit.*;
import com.getsentry.raven.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Test(singleThreaded = true)
public class RavenFactoryTest {
    @Tested
    private RavenFactory ravenFactory = null;
    @Injectable
    private ServiceLoader<RavenFactory> mockServiceLoader = null;

    @BeforeMethod
    public void setUp() throws Exception {
        setFinalStatic(RavenFactory.class.getDeclaredField("AUTO_REGISTERED_FACTORIES"), mockServiceLoader);

        new Expectations() {{
            mockServiceLoader.iterator();
            result = Collections.emptyIterator();
        }};
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the registered factories
        setFinalStatic(RavenFactory.class.getDeclaredField("MANUALLY_REGISTERED_FACTORIES"), new HashSet<>());
        setFinalStatic(RavenFactory.class.getDeclaredField("AUTO_REGISTERED_FACTORIES"), ServiceLoader.load(RavenFactory.class));
    }

    private void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    @Test
    public void testGetFactoriesFromServiceLoader(@Injectable final Raven mockRaven,
                                                  @Injectable final Dsn mockDsn) throws Exception {
        new Expectations() {{
            mockServiceLoader.iterator();
            result = new Delegate<Iterator<RavenFactory>>() {
                @SuppressWarnings("unused")
                public Iterator<RavenFactory> iterator() {
                    return Collections.singletonList(ravenFactory).iterator();
                }
            };
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }

    @Test
    public void testGetFactoriesManuallyAdded(@Injectable final Raven mockRaven,
                                              @Injectable final Dsn mockDsn) throws Exception {
        RavenFactory.registerFactory(ravenFactory);
        new Expectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn);

        assertThat(raven, is(mockRaven));
    }

    @Test
    public void testRavenInstanceForFactoryNameSucceedsIfFactoryFound(@Injectable final Raven mockRaven,
                                                                      @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = ravenFactory.getClass().getName();
        RavenFactory.registerFactory(ravenFactory);
        new Expectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(mockDsn, factoryName);

        assertThat(raven, is(mockRaven));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRavenInstanceForFactoryNameFailsIfNoFactoryFound(@Injectable final Raven mockRaven,
                                                                     @Injectable final Dsn mockDsn) throws Exception {
        String factoryName = "invalidName";
        RavenFactory.registerFactory(ravenFactory);
        RavenFactory.ravenInstance(mockDsn, factoryName);
    }

    @Test
    public void testRavenInstantiationFailureCaught(@Injectable final Dsn mockDsn) throws Exception {
        RavenFactory.registerFactory(ravenFactory);
        Exception exception = null;
        new Expectations() {{
            ravenFactory.createRavenInstance(mockDsn);
            result = new RuntimeException();
        }};

        try {
            RavenFactory.ravenInstance(mockDsn);
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception, notNullValue());
    }

    @Test
    public void testAutoDetectDsnIfNotProvided(@Injectable final Raven mockRaven,
                                               @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/3";
        RavenFactory.registerFactory(ravenFactory);
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsn;

            ravenFactory.createRavenInstance((Dsn) any);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance();

        assertThat(raven, is(mockRaven));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }

    @Test
    public void testCreateDsnIfStringProvided(@Injectable final Raven mockRaven,
                                              @SuppressWarnings("unused") @Mocked final Dsn mockDsn) throws Exception {
        final String dsn = "protocol://user:password@host:port/2";
        RavenFactory.registerFactory(ravenFactory);
        new Expectations() {{
            ravenFactory.createRavenInstance((Dsn) any);
            result = mockRaven;
        }};

        Raven raven = RavenFactory.ravenInstance(dsn);

        assertThat(raven, is(mockRaven));
        new Verifications() {{
            new Dsn(dsn);
        }};
    }
}
