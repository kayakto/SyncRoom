## 📘 Инструкция для Android разработчиков: Интеграция OAuth (VK и Yandex)

> **Важно для Android разработчиков:** Backend готов к работе с Android приложением. Backend просто принимает access_token через endpoint `/api/auth/oauth`. Вся OAuth авторизация происходит на стороне Android приложения.

## 🔵 Интеграция VK OAuth

> **Важно для Android разработчиков:** Backend готов к работе с Android приложением. Backend просто принимает VK access_token через endpoint `/api/auth/oauth`. Вся OAuth авторизация происходит на стороне Android приложения.

### Шаг 1: Регистрация приложения в VK (для Android разработчиков)

1. **Перейдите на страницу управления приложениями VK:**
    - Откройте в браузере: https://vk.com/apps?act=manage
    - Войдите в свой аккаунт VK (если не авторизованы)

2. **Создайте новое приложение:**
    - Нажмите кнопку **"Создать приложение"** или **"Создать"**
    - Выберите тип приложения: **"Android"** или **"Standalone приложение"**
    - Заполните форму:
      - **Название приложения**: например, "SyncRoom"
      - **Описание**: краткое описание вашего приложения
      - **Платформа**: выберите "Android"
    - Нажмите **"Подключить"**

3. **Настройте приложение:**
    - После создания приложения вы попадете на страницу настроек
    - В разделе **"Настройки"** найдите:
      - **ID приложения** (Application ID / Client ID) - это ваш `CLIENT_ID` (нужен для Android приложения)
      - **Защищенный ключ** (Secure key / Client Secret) - это ваш `CLIENT_SECRET` (обычно не нужен для Android, но может понадобиться)
    - ⚠️ **Важно**: Защищенный ключ показывается только один раз при создании! Сохраните его в безопасном месте.

4. **Настройте Redirect URI для Android:**
    - В разделе **"Настройки"** → **"Доверенный redirect URI"** добавьте:
      - Custom scheme для вашего приложения: `syncroom://auth/vk/callback` (замените `syncroom` на ваш package name или название)
      - Или используйте стандартный формат: `vk{CLIENT_ID}://authorize`
    - Сохраните изменения

5. **Настройте права доступа (Scope):**
    - В разделе **"Настройки"** → **"Права доступа"** включите:
      - ✅ **email** - для получения email пользователя (обязательно!)
      - ✅ **photos** - для получения аватара пользователя (опционально)

### Шаг 2: Получение VK Access Token в Android приложении

OAuth flow для Android состоит из следующих этапов:

#### Вариант A: Использование VK SDK для Android (рекомендуется)

VK предоставляет официальный SDK для Android, который упрощает OAuth авторизацию:

1. **Добавьте VK SDK в ваш проект:**

```gradle
// build.gradle (app level)
dependencies {
    implementation 'com.vk:android-sdk-core:4.0.1'
    implementation 'com.vk:android-sdk-api:4.0.1'
}
```

2. **Инициализируйте VK SDK в Application классе:**

```kotlin
// Application.kt
import com.vk.sdk.VKSdk

class SyncRoomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VKSdk.initialize(this)
    }
}
```

3. **Настройте AndroidManifest.xml:**

```xml
<activity
    android:name="com.vk.sdk.VKServiceActivity"
    android:launchMode="singleTop"
    android:theme="@android:style/Theme.Translucent.NoTitleBar" />

<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="vk{CLIENT_ID}" />
</intent-filter>
```

4. **Реализуйте авторизацию в Activity/Fragment:**

```kotlin
// MainActivity.kt или LoginFragment.kt
import com.vk.sdk.VKScope
import com.vk.sdk.VKSdk
import com.vk.sdk.api.VKApi
import com.vk.sdk.api.VKApiConst
import com.vk.sdk.api.VKParameters
import com.vk.sdk.api.VKRequest
import com.vk.sdk.api.auth.VKAuthCallback
import com.vk.sdk.api.auth.VKAccessToken
import com.vk.sdk.api.auth.VKAuthResult

class LoginActivity : AppCompatActivity() {
    private val CLIENT_ID = "YOUR_VK_CLIENT_ID" // Замените на ваш Client ID
    
    fun loginWithVk() {
        val scope = arrayOf(VKScope.EMAIL, VKScope.PHOTOS)
        VKSdk.login(this, scope)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val callback = object : VKAuthCallback {
            override fun onLogin(res: VKAuthResult) {
                if (res.authState == VKAuthResult.AuthState.SUCCESS) {
                    val token = res.token
                    // token.accessToken - это VK access token
                    // Теперь отправляем его на ваш backend
                    sendTokenToBackend(token.accessToken)
                } else if (res.authState == VKAuthResult.AuthState.ERROR) {
                    // Обработка ошибки
                    Log.e("VK", "Auth error: ${res.error}")
                }
            }
            
            override fun onLoginFailed(errorCode: Int) {
                // Обработка ошибки
                Log.e("VK", "Login failed with code: $errorCode")
            }
        }
        
        if (VKSdk.onActivityResult(requestCode, resultCode, data, callback)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    
    private fun sendTokenToBackend(vkAccessToken: String) {
        // Отправляем токен на ваш backend
        loginToSyncRoom(vkAccessToken)
    }
}
```

#### Вариант B: Ручная реализация OAuth flow (без SDK)

Если вы не хотите использовать VK SDK, можете реализовать OAuth flow вручную:

1. **Откройте WebView с URL авторизации VK:**

```kotlin
fun loginWithVk() {
    val CLIENT_ID = "YOUR_VK_CLIENT_ID"
    val REDIRECT_URI = "syncroom://auth/vk/callback" // Custom scheme
    val SCOPE = "email"
    
    val authUrl = "https://oauth.vk.com/authorize?" +
            "client_id=$CLIENT_ID&" +
            "redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}&" +
            "response_type=code&" +
            "scope=$SCOPE&" +
            "display=mobile&" +
            "v=5.131"
    
    // Откройте WebView или Chrome Custom Tabs
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
    startActivity(intent)
}
```

2. **Обработайте callback через Intent Filter:**

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".VkCallbackActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="syncroom" android:host="auth" />
    </intent-filter>
</activity>
```

```kotlin
// VkCallbackActivity.kt
class VkCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent.data
        val code = uri?.getQueryParameter("code")
        val error = uri?.getQueryParameter("error")
        
        if (error != null) {
            // Обработка ошибки
            finish()
            return
        }
        
        if (code != null) {
            // Обмениваем code на access_token
            exchangeCodeForToken(code)
        }
    }
    
    private fun exchangeCodeForToken(code: String) {
        // ⚠️ ВАЖНО: Этот запрос должен выполняться на вашем BACKEND!
        // НЕ делайте его напрямую из Android приложения!
        // Создайте endpoint на backend для обмена code на token
        
        val request = Request.Builder()
            .url("https://your-backend.com/api/auth/vk/exchange")
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                JSONObject().apply { put("code", code) }.toString()
            ))
            .build()
        
        // ... выполните запрос и получите access_token
    }
}
```

### Шаг 3: Отправка VK Access Token на Backend SyncRoom

После получения VK access_token (через SDK или вручную), отправьте его на ваш backend:

```kotlin
// AuthService.kt или ApiService.kt
import okhttp3.*
import org.json.JSONObject

class AuthService {
    private val BASE_URL = "http://your-backend-url:8080" // Замените на ваш URL
    
    suspend fun loginWithVk(vkAccessToken: String): AuthResponse {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("provider", "vk")
            put("accessToken", vkAccessToken)
        }
        
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/oauth")
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                json.toString()
            ))
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body()?.string()
        
        if (!response.isSuccessful) {
            throw Exception("Auth failed: ${response.code()}")
        }
        
        // Парсим ответ
        val jsonResponse = JSONObject(responseBody)
        val authResponse = AuthResponse(
            accessToken = jsonResponse.getString("accessToken"),
            refreshToken = jsonResponse.getString("refreshToken"),
            isFirstLogin = jsonResponse.getBoolean("isFirstLogin"),
            user = parseUser(jsonResponse.getJSONObject("user"))
        )
        
        // Сохраните токены в SharedPreferences или SecureStorage
        saveTokens(authResponse.accessToken, authResponse.refreshToken)
        
        return authResponse
    }
    
    private fun saveTokens(accessToken: String, refreshToken: String) {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .apply()
    }
}
```

**Или используя Retrofit (рекомендуется):**

```kotlin
// ApiService.kt
import retrofit2.http.*

interface SyncRoomApi {
    @POST("/api/auth/oauth")
    suspend fun loginWithOAuth(@Body request: OAuthRequest): Response<AuthResponse>
}

data class OAuthRequest(
    val provider: String,
    val accessToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val isFirstLogin: Boolean,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String?,
    val provider: String,
    val avatarUrl: String?
)

// Использование:
val api = Retrofit.Builder()
    .baseUrl("http://your-backend-url:8080")
    .build()
    .create(SyncRoomApi::class.java)

val response = api.loginWithOAuth(OAuthRequest("vk", vkAccessToken))
```

**Ответ от SyncRoom API:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "isFirstLogin": true,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Иван Иванов",
    "email": "ivan@example.com",
    "provider": "vk",
    "avatarUrl": "https://vk.com/images/user_200.jpg"
  }
}
```

### Полный пример кода для Android (Kotlin)

```kotlin
// VkAuthManager.kt
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vk.sdk.VKScope
import com.vk.sdk.VKSdk
import com.vk.sdk.api.auth.VKAuthCallback
import com.vk.sdk.api.auth.VKAuthResult
import com.vk.sdk.api.auth.VKAccessToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VkAuthManager(private val context: Context) {
    private val CLIENT_ID = "YOUR_VK_CLIENT_ID"
    private val BACKEND_URL = "http://your-backend-url:8080"
    
    private val api: SyncRoomApi = Retrofit.Builder()
        .baseUrl(BACKEND_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SyncRoomApi::class.java)
    
    fun loginWithVk(activity: Activity) {
        val scope = arrayOf(VKScope.EMAIL, VKScope.PHOTOS)
        VKSdk.login(activity, scope)
    }
    
    fun handleVkAuthResult(result: VKAuthResult, callback: (Result<AuthResponse>) -> Unit) {
        when (result.authState) {
            VKAuthResult.AuthState.SUCCESS -> {
                val vkToken = result.token
                // Отправляем VK access token на backend
                sendToBackend(vkToken.accessToken, callback)
            }
            VKAuthResult.AuthState.ERROR -> {
                callback(Result.failure(Exception("VK auth error: ${result.error}")))
            }
            else -> {
                callback(Result.failure(Exception("VK auth cancelled")))
            }
        }
    }
    
    private fun sendToBackend(vkAccessToken: String, callback: (Result<AuthResponse>) -> Unit) {
        // Используем Coroutines для асинхронного запроса
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = OAuthRequest(provider = "vk", accessToken = vkAccessToken)
                val response = api.loginWithOAuth(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!
                    // Сохраняем токены
                    saveTokens(authData.accessToken, authData.refreshToken)
                    callback(Result.success(authData))
                } else {
                    callback(Result.failure(Exception("Backend auth failed: ${response.code()}")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
    
    private fun saveTokens(accessToken: String, refreshToken: String) {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .apply()
    }
}

// LoginActivity.kt
class LoginActivity : AppCompatActivity() {
    private lateinit var vkAuthManager: VkAuthManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        vkAuthManager = VkAuthManager(this)
        
        findViewById<Button>(R.id.btnVkLogin).setOnClickListener {
            vkAuthManager.loginWithVk(this)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val callback = object : VKAuthCallback {
            override fun onLogin(res: VKAuthResult) {
                vkAuthManager.handleVkAuthResult(res) { result ->
                    runOnUiThread {
                        result.onSuccess { authResponse ->
                            // Успешный вход
                            Log.d("Auth", "User logged in: ${authResponse.user.name}")
                            // Переходим в главное приложение
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }.onFailure { error ->
                            // Ошибка входа
                            Log.e("Auth", "Login failed", error)
                            Toast.makeText(this@LoginActivity, "Ошибка входа: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            override fun onLoginFailed(errorCode: Int) {
                Log.e("VK", "Login failed with code: $errorCode")
                Toast.makeText(this@LoginActivity, "Ошибка авторизации VK", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (VKSdk.onActivityResult(requestCode, resultCode, data, callback)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
```

### Возможные ошибки и их решение

1. **"Invalid redirect_uri"**
    - Убедитесь, что redirect_uri в настройках VK совпадает с custom scheme в AndroidManifest.xml
    - Для VK SDK используйте формат `vk{CLIENT_ID}://authorize`
    - Для ручной реализации используйте ваш custom scheme (например, `syncroom://auth/vk/callback`)

2. **"Invalid client_id"**
    - Проверьте, что используете правильный Client ID из настроек приложения VK
    - Убедитесь, что приложение создано как "Android" приложение в VK

3. **"Invalid VK access token" (от SyncRoom Backend)**
    - Токен истек (VK токены живут ограниченное время, обычно 12 часов)
    - Токен невалиден или был отозван
    - Повторите OAuth flow для получения нового токена
    - Backend проверяет токен через VK API, если VK API вернул ошибку - токен невалиден

4. **"User denied access"**
    - Пользователь отменил авторизацию в VK
    - Обработайте это в callback

5. **Проблемы с VK SDK**
    - Убедитесь, что VK SDK правильно инициализирован в Application классе
    - Проверьте, что все зависимости добавлены в build.gradle
    - Проверьте логи на наличие ошибок инициализации

### Безопасность для Android

- ✅ **Никогда не храните `client_secret` в Android приложении** - он должен быть только на backend
- ✅ **Используйте HTTPS для всех запросов к backend** (не http://localhost в продакшене!)
- ✅ **Храните JWT токены в EncryptedSharedPreferences или KeyStore** (не в обычном SharedPreferences)
- ✅ **Валидируйте сертификаты SSL при подключении к backend**
- ✅ **Используйте ProGuard/R8 для обфускации кода в release сборках**
- ✅ **Не логируйте токены и чувствительные данные**

### Важные замечания для Android разработчиков

1. **Backend готов к работе** - вам не нужно ничего менять на backend. Просто отправляйте VK access_token на `/api/auth/oauth`

2. **OAuth flow происходит на Android** - весь процесс авторизации VK должен быть реализован в Android приложении

3. **Backend только проверяет токен** - backend получает VK access_token, проверяет его через VK API, и если токен валиден - создает/находит пользователя и выдает JWT токены

4. **Не нужно обменивать code на token на backend** - если вы используете VK SDK, он сам получает access_token. Если делаете вручную, можете либо обменять code на token на backend (создав endpoint), либо на Android (но тогда нужен client_secret, что небезопасно)

## 🟡 Интеграция Yandex ID OAuth

### Шаг 1: Регистрация приложения в Yandex (для Android разработчиков)

1. **Перейдите на страницу управления приложениями Yandex:**
    - Откройте в браузере: https://oauth.yandex.ru/
    - Войдите в свой аккаунт Yandex (если не авторизованы)
    - Перейдите в раздел "Мои приложения" или "Управление приложениями"

2. **Создайте новое приложение:**
    - Нажмите кнопку **"Создать новое приложение"** или **"Зарегистрировать"**
    - Заполните форму:
      - **Название приложения**: например, "SyncRoom"
      - **Описание**: краткое описание вашего приложения
      - **Платформа**: выберите "Мобильное приложение" или "Веб-сервис"
    - Нажмите **"Создать"**

3. **Настройте приложение:**
    - После создания приложения вы попадете на страницу настроек
    - В разделе **"Общие настройки"** найдите:
      - **ID приложения** (Client ID) - это ваш `CLIENT_ID` (нужен для Android приложения)
      - **Пароль приложения** (Client Secret) - это ваш `CLIENT_SECRET` (обычно не нужен для Android, но может понадобиться)
    - ⚠️ **Важно**: Пароль приложения показывается только один раз при создании! Сохраните его в безопасном месте.

4. **Настройте Redirect URI для Android:**
    - В разделе **"Платформы"** → **"Redirect URI"** добавьте:
      - Custom scheme для вашего приложения: `syncroom://auth/yandex/callback` (замените `syncroom` на ваш package name или название)
      - Или используйте стандартный формат: `yandexauth://` (для Yandex ID SDK)
    - Сохраните изменения

5. **Настройте права доступа (Scope):**
    - В разделе **"Права доступа"** включите:
      - ✅ **login:email** - для получения email пользователя (обязательно!)
      - ✅ **login:avatar** - для получения аватара пользователя (опционально)
      - ✅ **login:name** - для получения имени пользователя (опционально)

### Шаг 2: Получение Yandex Access Token в Android приложении

OAuth flow для Yandex ID аналогичен VK:

#### Вариант A: Использование Yandex ID SDK для Android (рекомендуется)

Yandex предоставляет официальный SDK для Android:

1. **Добавьте Yandex ID SDK в ваш проект:**

```gradle
// build.gradle (app level)
dependencies {
    implementation 'com.yandex.android:authsdk:2.5.1'
}
```

2. **Инициализируйте Yandex ID SDK в Application классе:**

```kotlin
// Application.kt
import com.yandex.authsdk.YandexAuthSdk

class SyncRoomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Yandex SDK инициализируется автоматически
    }
}
```

3. **Настройте AndroidManifest.xml:**

```xml
<activity
    android:name="com.yandex.authsdk.internal.ExternalBrowserActivity"
    android:exported="true" />

<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="yandexauth" />
</intent-filter>
```

4. **Реализуйте авторизацию в Activity/Fragment:**

```kotlin
// MainActivity.kt или LoginFragment.kt
import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken

class LoginActivity : AppCompatActivity() {
    private val CLIENT_ID = "YOUR_YANDEX_CLIENT_ID" // Замените на ваш Client ID
    private lateinit var yandexAuthSdk: YandexAuthSdk
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yandexAuthSdk = YandexAuthSdk(this)
    }
    
    fun loginWithYandex() {
        val loginOptions = YandexAuthLoginOptions.Builder(this)
            .setClientId(CLIENT_ID)
            .build()
        
        yandexAuthSdk.login(loginOptions)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (yandexAuthSdk.onActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    
    private fun handleYandexAuthResult(result: Result<YandexAuthToken>) {
        result.onSuccess { token ->
            // token.value - это Yandex access token
            // Теперь отправляем его на ваш backend
            sendTokenToBackend(token.value)
        }.onFailure { error ->
            if (error is YandexAuthException) {
                Log.e("Yandex", "Auth error: ${error.type}")
            } else {
                Log.e("Yandex", "Auth failed", error)
            }
        }
    }
    
    private fun sendTokenToBackend(yandexAccessToken: String) {
        // Отправляем токен на ваш backend
        loginToSyncRoom(yandexAccessToken)
    }
}
```

#### Вариант B: Ручная реализация OAuth flow (без SDK)

Если вы не хотите использовать Yandex ID SDK, можете реализовать OAuth flow вручную:

1. **Откройте WebView с URL авторизации Yandex:**

```kotlin
fun loginWithYandex() {
    val CLIENT_ID = "YOUR_YANDEX_CLIENT_ID"
    val REDIRECT_URI = "syncroom://auth/yandex/callback" // Custom scheme
    val SCOPE = "login:email login:avatar login:name"
    
    val authUrl = "https://oauth.yandex.ru/authorize?" +
            "response_type=token&" +
            "client_id=$CLIENT_ID&" +
            "redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}&" +
            "scope=$SCOPE"
    
    // Откройте WebView или Chrome Custom Tabs
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
    startActivity(intent)
}
```

2. **Обработайте callback через Intent Filter:**

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".YandexCallbackActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="syncroom" android:host="auth" android:pathPrefix="/yandex" />
    </intent-filter>
</activity>
```

```kotlin
// YandexCallbackActivity.kt
class YandexCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent.data
        val fragment = uri?.fragment // Yandex возвращает токен в fragment, а не в query
        
        if (fragment != null) {
            val params = fragment.split("&").associate { 
                val (key, value) = it.split("=", limit = 2)
                key to value
            }
            
            val accessToken = params["access_token"]
            val error = params["error"]
            
            if (error != null) {
                // Обработка ошибки
                finish()
                return
            }
            
            if (accessToken != null) {
                // Отправляем токен на backend
                sendToBackend(accessToken)
            }
        }
    }
    
    private fun sendToBackend(accessToken: String) {
        // Отправляем на ваш backend
        loginToSyncRoom(accessToken)
    }
}
```

### Шаг 3: Отправка Yandex Access Token на Backend SyncRoom

После получения Yandex access_token, отправьте его на ваш backend:

```kotlin
// AuthService.kt или ApiService.kt
suspend fun loginWithYandex(yandexAccessToken: String): AuthResponse {
    val json = JSONObject().apply {
        put("provider", "yandex")
        put("accessToken", yandexAccessToken)
    }
    
    val request = Request.Builder()
        .url("$BASE_URL/api/auth/oauth")
        .post(RequestBody.create(
            MediaType.parse("application/json"),
            json.toString()
        ))
        .build()
    
    val response = client.newCall(request).execute()
    // ... обработка ответа аналогично VK
}
```

**Или используя Retrofit:**

```kotlin
val response = api.loginWithOAuth(OAuthRequest("yandex", yandexAccessToken))
```

**Ответ от SyncRoom API:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "isFirstLogin": true,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Иван Иванов",
    "email": "ivan@yandex.ru",
    "provider": "yandex",
    "avatarUrl": "https://avatars.yandex.net/get-yapic/123/islands-200"
  }
}
```

### Возможные ошибки и их решение (Yandex)

1. **"Invalid redirect_uri"**
    - Убедитесь, что redirect_uri в настройках Yandex совпадает с custom scheme в AndroidManifest.xml
    - Для Yandex ID SDK используйте формат `yandexauth://`
    - Для ручной реализации используйте ваш custom scheme (например, `syncroom://auth/yandex/callback`)

2. **"Invalid client_id"**
    - Проверьте, что используете правильный Client ID из настроек приложения Yandex

3. **"Invalid Yandex access token" (от SyncRoom Backend)**
    - Токен истек (Yandex токены живут ограниченное время)
    - Токен невалиден или был отозван
    - Повторите OAuth flow для получения нового токена
    - Backend проверяет токен через Yandex API, если Yandex API вернул ошибку - токен невалиден

### Дополнительные ресурсы

- [Официальная документация VK OAuth](https://dev.vk.com/ru/api/access-token/authcode-flow-user)
- [VK API методы](https://dev.vk.com/ru/api/overview)
- [Официальная документация Yandex OAuth](https://yandex.ru/dev/id/doc/ru/)
- [Yandex ID SDK для Android](https://github.com/yandex/yandex-login-sdk-android)