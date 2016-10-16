package uk.q3c.build.creator

import com.google.inject.Inject

/**
 * Created by David Sowerby on 10 Oct 2016
 */
class DefaultProjectCreator @Inject constructor(val builders: MutableSet<Builder>, val configuration: ProjectConfiguration) : ProjectCreator, ProjectConfiguration by configuration {

    init {
        for (builder in builders) {
            builder.setProjectCreator(this)
        }
    }


    override fun execute() {
        for (step in configuration.getSteps()) {
            for (builder in builders) {
                when (step) {
                    is SourceLanguage -> builder.set(step)
                    is TestSet -> builder.set(step)
                    else -> {
                        throw UnknownStepException(step.javaClass.name + " is unknown")
                    }
                }
            }
        }
        if (useMavenPublishing) {
            for (builder in builders) {
                builder.mavenPublishing()
            }
        }

        // This will create repos if config correctly set up
        configuration.gitPlus.createOrVerifyRepos()

        // this could duplicate the merge if already done by previous step.  This shold be fixable when gitPlus API fixed
        // see https://github.com/davidsowerby/gitplus/issues/77
        if (configuration.mergeIssueLabels) {
            configuration.gitPlus.gitRemote.mergeLabels()
        }
    }

    override fun buildersCount(): Int {
        return builders.size
    }


}

class UnknownStepException(msg: String) : Throwable(msg) {

}
