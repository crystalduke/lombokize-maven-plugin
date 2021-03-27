package com.github.crystalduke.lombok;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

public class ApplyLombokMojoTest {

    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    /**
     * 現時点では詳細なテストは実装していない.
     *
     * @throws Exception if any
     */
    @Test
    public void testSomething() throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertTrue(pom.exists());

        ApplyLombokMojo myMojo = (ApplyLombokMojo) rule.lookupConfiguredMojo(pom, "apply");
        assertNotNull(myMojo);
        assertNotNull(rule.getVariableValueFromObject(myMojo, "sourceDirectory"));
        assertNotNull(rule.getVariableValueFromObject(myMojo, "encoding"));
        assertNotNull(rule.getVariableValueFromObject(myMojo, "languageLevel"));

        myMojo.execute();
    }

    /**
     * Do not need the MojoRule.
     */
    @WithoutMojo
    @Test
    public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
        assertTrue(true);
    }
}
