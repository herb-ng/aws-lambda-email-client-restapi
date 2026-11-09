package com.example.email;

import java.io.InputStream;
import java.util.List;

public interface EmailStore {

    List<String> listKeys();

    InputStream open(String key);
}
