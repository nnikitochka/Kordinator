package su.nnedition.kord.security

import java.lang.reflect.InvocationTargetException
import su.nnedition.kord.domain.User
import su.nnedition.kord.domain.UserCredential
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserIdentityTest {
    @Test
    fun snapshotsCredentialsAndRoles() {
        val credentials = mutableListOf<UserCredential>()
        val roles = mutableSetOf(User.Role.STREAM)
        val identity = UserIdentity("alice", credentials, roles)

        credentials += UserCredential("alice", "alice", "secret", "noop", UserCredential.App.KORDINATOR, "test")
        roles += User.Role.ADMIN

        assertThat(identity.credentials).isEmpty()
        assertThat(identity.roles).containsExactly(User.Role.STREAM)
        assertThatThrownBy { (identity.roles as MutableSet<User.Role>).add(User.Role.ADMIN) }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun rejectsNullFieldsAtTheJavaConstructorBoundary() {
        assertConstructorRejectsNull(null, emptyList<UserCredential>(), emptySet<User.Role>())
        assertConstructorRejectsNull("alice", null, emptySet<User.Role>())
        assertConstructorRejectsNull("alice", emptyList<UserCredential>(), null)
    }

    private fun assertConstructorRejectsNull(username: Any?, credentials: Any?, roles: Any?) {
        assertThatThrownBy {
            try {
                UserIdentity::class.java
                    .getConstructor(String::class.java, List::class.java, Set::class.java)
                    .newInstance(username, credentials, roles)
            } catch (exception: InvocationTargetException) {
                throw exception.targetException
            }
        }.isInstanceOf(NullPointerException::class.java)
    }
}
