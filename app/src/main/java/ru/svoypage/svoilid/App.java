package ru.svoypage.svoilid;

import android.app.Application;

public class App extends Application {

    private static App instance;
    private Session session;
    private ApiClient api;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        session = new Session(this);
        api = new ApiClient(session);
    }

    public static App get() { return instance; }
    public Session session() { return session; }
    public ApiClient api() { return api; }
}
