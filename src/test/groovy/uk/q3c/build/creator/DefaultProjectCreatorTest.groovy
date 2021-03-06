package uk.q3c.build.creator

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.inject.Guice
import com.google.inject.Injector
import org.jetbrains.annotations.NotNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification
import uk.q3c.build.gitplus.gitplus.GitPlus
import uk.q3c.build.gitplus.local.GitLocal
import uk.q3c.build.gitplus.remote.GitRemote
/**
 * Created by David Sowerby on 10 Oct 2016
 */
class DefaultProjectCreatorTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder
    File temp

    MockBuilder builder1 = new MockBuilder()
    MockBuilder builder2 = new MockBuilder()
    ProjectConfiguration configuration = Mock(ProjectConfiguration)
    ProjectCreator creator
    GitPlus gitPlus = Mock(GitPlus)
    GitRemote gitRemote = Mock(GitRemote)
    GitLocal gitLocal = Mock()
    File projectDir

    def setup() {
        temp = temporaryFolder.getRoot()
        projectDir = new File(temp, "wiggly")
        configuration.getGitPlus() >> gitPlus
        gitPlus.local >> gitLocal
        gitPlus.remote >> gitRemote
        configuration.projectDir >> projectDir
    }


    def "module configuration"() {
        given:
        Injector injector = Guice.createInjector(new ProjectCreatorModule())


        when:
        creator = injector.getInstance(ProjectCreator)

        then:
        creator instanceof DefaultProjectCreator
        creator.buildersCount() == 2
    }

    def "execute calls each builder with each step, and mavenPublishing if true and executes the builders"() {
        given:
        SourceLanguage sourceLanguage1 = new SourceLanguage(Language.JAVA, "1.8")
        SourceLanguage sourceLanguage2 = new SourceLanguage(Language.KOTLIN, "1.0.4")
        TestSet testSet1 = new TestSet("integrationTest", TestFramework.SPOCK, "groovy2.4")
        TestSet testSet2 = new TestSet("integrationTest", TestFramework.JUNIT, "4.10")
        Set<Builder> builders = ImmutableSet.of(builder1, builder2)
        List<ConfigStep> steps = ImmutableList.of(sourceLanguage1, testSet2, sourceLanguage2, testSet1)
        configuration.getSteps() >> steps
        configuration.useMavenPublishing >> true
        creator = new DefaultProjectCreator(builders, configuration)

        when:
        creator.execute()

        then:
        builder1.mavenPublishingCalled
        builder2.mavenPublishingCalled
        builder1.sourceLangugageCalled == 2
        builder2.sourceLangugageCalled == 2
        builder1.testSetCalled == 2
        builder2.testSetCalled == 2
        builder1.sourceLanguages.containsAll(sourceLanguage1, sourceLanguage2)
        builder2.sourceLanguages.containsAll(sourceLanguage1, sourceLanguage2)
        builder1.testSets.containsAll(testSet1, testSet2)
        builder2.testSets.containsAll(testSet1, testSet2)
        builder1.testSets.get(0) == testSet2
        builder2.testSets.get(0) == testSet2
        builder1.sourceLanguages.get(0) == sourceLanguage1
        builder2.sourceLanguages.get(0) == sourceLanguage1

        then:
        builder1.executeCalled == 1
        builder2.executeCalled == 1

        then:
        1 * gitPlus.execute()
    }

    def "delegation to project configuration"() {

        given:
        Set<Builder> builders = ImmutableSet.of(builder1, builder2)
        creator = new DefaultProjectCreator(builders, configuration)
        File projectDir = new File("/home/temp")

        when:
        creator.setBasePackage("x")
        creator.setProjectDir(projectDir)

        then:
        1 * configuration.setProjectDir(projectDir)
        1 * configuration.setBasePackage("x")
    }

    def "issue labels configuration passed to GitPlus"() {
        given:
        Injector injector = Guice.createInjector(new ProjectCreatorModule())
        creator = injector.getInstance(ProjectCreator)
        Map<String, String> labels = ImmutableMap.of("a", "b")

        when:
        creator.mergeIssueLabels(true)
        creator.projectName("wiggly")
        creator.remoteRepoUser('davidsowerby')
        creator.issueLabels(labels)

        then:
        creator.gitPlus.remote.mergeIssueLabels
        creator.gitPlus.remote.repoName == 'wiggly'
        creator.gitPlus.local.projectName == 'wiggly'
        creator.gitPlus.remote.repoUser == 'davidsowerby'
        creator.gitPlus.remote.issueLabels == labels
    }

    def "update labels is called in GitRemote even when it is the only setting"() {
        given:
        gitPlus.getRemote() >> gitRemote
        configuration.getMergeIssueLabels() >> true
        configuration.getGitPlus() >> gitPlus
        configuration.getSteps() >> ImmutableList.of()
        Set<Builder> builders = ImmutableSet.of(builder1, builder2)
        creator = new DefaultProjectCreator(builders, configuration)

        when:
        creator.execute()

        then:
        1 * gitRemote.mergeLabels()


    }

//    def "update labels - for real"(){
//        given:
//        Injector injector = Guice.createInjector(new ProjectCreatorModule())
//        creator = injector.getInstance(ProjectCreator)
//
//        when:
//        creator.mergeIssueLabels(true)
//        creator.projectName("gitplus")
//        creator.remoteRepoUser('davidsowerby')
//        creator.gitPlus.gitRemote.mergeLabels()
//
//        then:
//        true
//
//    }

    @Ignore
    def "create a new project - for real"() {
        given:
        Injector injector = Guice.createInjector(new ProjectCreatorModule())
        creator = injector.getInstance(ProjectCreator)
        creator.createNewProject = true
        creator.publicProject = true
        creator.projectName = 'kaytee-test-delegate'
        creator.projectUserName = 'davidsowerby'
        creator.mergeIssueLabels = true
        creator.basePackage = 'uk.q3c.kaytee'
        creator.useMavenPublishing = false
//        creator.baseVersion('0.0.0.1')
        creator.source(Language.JAVA, '').source(Language.KOTLIN, '')
//        creator.testSet('test', TestFramework.SPOCK, "")
        File gitDir = new File("/home/david/git")
        creator.projectDir = new File(gitDir, creator.projectName)

        when:
        creator.execute()

        then:
        true

    }


    class MockBuilder implements Builder {


        ProjectCreator creator
        int sourceLangugageCalled
        int testSetCalled
        int executeCalled
        boolean mavenPublishingCalled
        List<SourceLanguage> sourceLanguages = new ArrayList<>()
        List<TestSet> testSets = new ArrayList<>()

        @Override
        void execute() {
            executeCalled++
        }

        @Override
        void configParam(ConfigStep configStep) {
            if (configStep instanceof SourceLanguage) {
                configSourceLanguage(configStep)
            } else if (configStep instanceof TestSet) {
                configTestSet(configStep)
            }
        }

        void configSourceLanguage(@NotNull SourceLanguage step) {
            sourceLangugageCalled++
            sourceLanguages.add(step)
        }

        void configTestSet(@NotNull TestSet step) {
            testSetCalled++
            testSets.add(step)
        }

        @Override
        void mavenPublishing() {
            mavenPublishingCalled = true
        }

        @Override
        void projectCreator(@NotNull ProjectCreator creator) {

        }
    }
}
