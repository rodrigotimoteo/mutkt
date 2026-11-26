package com.github.rodrigotimoteo.mutkt.gradle

/**
 * Globs of the classes Android builds generate, which an Android project starts its `excludedClasses` with.
 * Each glob names a generated prefix, suffix or package; it also skips a user class that happens to follow it.
 * They are added when the Android plugin applies: with `apply plugin:` lines ordered MutKt, a script that sets
 * `excludedClasses`, then Android, they are appended to the user's value, which a `plugins { }` block avoids.
 */
internal val ANDROID_GENERATED_CLASSES: List<String> =
    listOf(
        // Resource ids (R, data binding's BR) and BuildConfig: only fields and initializers, which no operator mutates.
        "**.R",
        "**.R\$*",
        "**.BR",
        "**.BuildConfig",
        // Hilt: Hilt_MainActivity (with its anonymous classes), MainViewModel_HiltModules\$KeyModule and
        // DaggerApp_HiltComponents_SingletonC.
        "**.Hilt_*",
        "**.*_HiltModules*",
        "**.*_HiltComponents*",
        // Dagger: Repo_Factory (with its InstanceHolder), NetworkModule_ProvideApiFactory, MainActivity_MembersInjector
        // and DaggerAppComponent with its nested classes. Kotlin and Java class names rarely hold an underscore.
        "**.*_*Factory",
        "**.*_*Factory\$*",
        "**.*_MembersInjector",
        "**.Dagger*Component",
        "**.Dagger*Component\$*",
        // Assisted injection: the implementation of the @AssistedFactory interface RepoFactory.
        "**.*Factory_Impl",
        // View and data binding write into the namespace's databinding package: ActivityMainBinding,
        // ActivityMainBindingImpl or ActivityMainBindingLandImpl, and their listener classes.
        "**.databinding.*Binding",
        "**.databinding.*Binding*Impl",
        "**.databinding.*Binding*Impl\$*",
        "**.DataBinderMapperImpl",
        // Data binding's listener adapters, such as demo.generated.callback.OnClickListener.
        "**.generated.callback.*",
        // Compose: a file's singleton lambdas, ComposableSingletons\$MainScreenKt and its lambda classes.
        "**.ComposableSingletons\$*",
        // Compose live literals (debug builds): LiveLiterals\$MainScreenKt.
        "**.LiveLiterals\$*",
    )
