package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.Glob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidGeneratedClassesTest {
    // glob, a generated name it matches, a user name no default glob matches
    private val cases =
        listOf(
            Triple("**.R", "demo.R", "demo.Router"),
            Triple("**.R\$*", "demo.R\$string", "demo.Router\$Companion"),
            Triple("**.BR", "demo.BR", "demo.Bridge"),
            Triple("**.BuildConfig", "demo.BuildConfig", "demo.BuildConfigReader"),
            Triple("**.Hilt_*", "demo.Hilt_MainActivity\$1", "demo.HiltSetup"),
            Triple("**.*_HiltModules*", "demo.MainViewModel_HiltModules\$KeyModule", "demo.HiltModules"),
            Triple("**.*_HiltComponents*", "demo.DaggerApp_HiltComponents_SingletonC", "demo.AppHiltComponents"),
            Triple("**.*_*Factory", "demo.NetworkModule_ProvideApiFactory", "demo.ViewModelFactory"),
            Triple("**.*_*Factory\$*", "demo.Repo_Factory\$InstanceHolder", "demo.ViewModelFactory\$Companion"),
            Triple("**.*_MembersInjector", "demo.MainActivity_MembersInjector", "demo.MembersInjector"),
            Triple("**.Dagger*Component", "demo.DaggerAppComponent", "demo.DaggerComponentFactory"),
            Triple("**.Dagger*Component\$*", "demo.DaggerAppComponent\$Builder", "demo.AppComponent\$Builder"),
            Triple("**.*Factory_Impl", "demo.RepoFactory_Impl", "demo.RepoFactoryImpl"),
            Triple("**.databinding.*Binding", "demo.databinding.ActivityMainBinding", "demo.ui.ProfileBinding"),
            Triple(
                "**.databinding.*Binding*Impl",
                "demo.databinding.ActivityMainBindingLandImpl",
                "demo.ui.ProfileBindingImpl",
            ),
            Triple(
                "**.databinding.*Binding*Impl\$*",
                "demo.databinding.ActivityMainBindingImpl\$OnClickListenerImpl",
                "demo.databinding.BindingAdapters\$Companion",
            ),
            Triple("**.DataBinderMapperImpl", "androidx.databinding.DataBinderMapperImpl", "demo.DataBinderMapper"),
            Triple(
                "**.generated.callback.*",
                "demo.generated.callback.OnClickListener",
                "demo.callback.OnClickListener",
            ),
            Triple(
                "**.ComposableSingletons\$*",
                "demo.ComposableSingletons\$MainScreenKt\$lambda-1\$1",
                "demo.ComposableSingletonsKt",
            ),
            Triple("**.LiveLiterals\$*", "demo.LiveLiterals\$MainScreenKt", "demo.LiveLiterals"),
        )

    @Test
    fun `every default glob has a case`() {
        assertEquals(ANDROID_GENERATED_CLASSES, cases.map { it.first })
    }

    @Test
    fun `each glob matches its generated name and no default glob matches the user name`() {
        val globs = ANDROID_GENERATED_CLASSES.map(::Glob)
        val failures =
            cases.filter { (glob, generated, user) ->
                !Glob(glob).matches(generated) || globs.any { it.matches(user) }
            }

        assertEquals(emptyList<Triple<String, String, String>>(), failures)
    }

    @Test
    fun `a user class with an underscore that ends in Factory is skipped too, an accepted false positive`() {
        assertEquals(true, ANDROID_GENERATED_CLASSES.map(::Glob).any { it.matches("demo.Api_V2Factory") })
    }
}
