package com.nars.maplibre

import android.app.Application
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.data.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val application = mockk<Application>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { application.getString(R.string.login_failed) } returns "Login failed"
        every { application.getString(R.string.login_error) } returns "Connection error"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() = LoginViewModel(application, sessionManager)

    private fun LoginViewModel.fillCredentials() {
        onUsernameChange("ali")
        onPasswordChange("secret")
    }

    @Test
    fun `canSubmit requires both credentials`() {
        val vm = createViewModel()

        assertFalse(vm.canSubmit)

        vm.onUsernameChange("ali")
        assertFalse(vm.canSubmit)

        vm.onPasswordChange("secret")
        assertTrue(vm.canSubmit)
    }

    @Test
    fun `canSubmit rejects blank-only input`() {
        val vm = createViewModel()
        vm.onUsernameChange("   ")
        vm.onPasswordChange("  ")

        assertFalse(vm.canSubmit)
    }

    @Test
    fun `isLoggedIn delegates to session manager`() {
        every { sessionManager.isLoggedIn() } returns true

        assertTrue(createViewModel().isLoggedIn())
    }

    @Test
    fun `login success navigates once and clears loading`() = runTest {
        val response = LoginResponse(user = User(username = "ali", name = "Ali"))
        coEvery { sessionManager.login("ali", "secret") } returns Result.success(response)
        val vm = createViewModel()
        var navigations = 0

        vm.fillCredentials()
        vm.login { navigations++ }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, navigations)
        assertFalse(vm.isLoading.value)
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `login failure surfaces the backend message`() = runTest {
        coEvery { sessionManager.login(any(), any()) } returns Result.failure(Exception("bad credentials"))
        val vm = createViewModel()

        vm.fillCredentials()
        vm.login { }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.errorMessage.value.orEmpty().contains("bad credentials"))
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `login IOException surfaces the connection error prefix`() = runTest {
        coEvery { sessionManager.login(any(), any()) } throws IOException("network unreachable")
        val vm = createViewModel()

        vm.fillCredentials()
        vm.login { }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.errorMessage.value.orEmpty().contains("network unreachable"))
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `login with incomplete credentials does not call the session manager`() = runTest {
        val vm = createViewModel()
        vm.onUsernameChange("ali") // no password

        vm.login { }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { sessionManager.login(any(), any()) }
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `typing clears a previous error`() = runTest {
        coEvery { sessionManager.login(any(), any()) } returns Result.failure(Exception("nope"))
        val vm = createViewModel()

        vm.fillCredentials()
        vm.login { }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.errorMessage.value != null)

        vm.onUsernameChange("ali2")

        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `username is trimmed before authentication`() = runTest {
        val response = LoginResponse(user = User(username = "ali", name = "Ali"))
        coEvery { sessionManager.login("ali", "secret") } returns Result.success(response)
        val vm = createViewModel()

        vm.onUsernameChange("  ali ")
        vm.onPasswordChange("secret")
        vm.login { }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { sessionManager.login("ali", "secret") }
    }
}
