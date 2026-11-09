import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

fun Project.configureCommonsMavenPublication(
    publicationName: String,
    publicationDescription: String?,
    configureAuthors: MavenPom.() -> Unit = {
        organization { organization ->
            organization.name.set("Atto")
            organization.url.set("https://atto.cash")
        }
        developers { developers ->
            developers.developer { developer ->
                developer.id.set("atto")
                developer.name.set("Atto Team")
            }
        }
    },
) {
    val javadocJar =
        tasks.register("javadocJar", Jar::class.java) {
            it.archiveClassifier.set("javadoc")
            it.from(tasks.named("dokkaGenerateHtml"))
        }

    val publishing = extensions.getByType(PublishingExtension::class.java)
    publishing.publications.withType(MavenPublication::class.java).configureEach { publication ->
        publication.artifact(javadocJar)
        publication.pom { pom ->
            pom.name.set(publicationName)
            pom.description.set(publicationDescription)
            pom.url.set("https://github.com/attocash/commons")
            pom.configureAuthors()
            pom.licenses { licenses ->
                licenses.license { license ->
                    license.name.set("BSD 3-Clause License")
                    license.url.set("https://github.com/attocash/commons/blob/main/LICENSE")
                }
            }
            pom.scm { scm ->
                scm.connection.set("scm:git:git://github.com/attocash/commons.git")
                scm.developerConnection.set("scm:git:ssh://github.com/attocash/commons.git")
                scm.url.set("https://github.com/attocash/commons")
            }
        }
    }

    if (findProperty("signing.skip")?.toString()?.toBoolean() != true) {
        extensions.configure(SigningExtension::class.java) { signing ->
            signing.useInMemoryPgpKeys(findProperty("signingKey")?.toString(), "")
            signing.sign(publishing.publications)
        }
    }
}
