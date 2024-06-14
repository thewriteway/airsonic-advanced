# Setting up Apache

The following configurations works for HTTPS (with an HTTP redirection).

> NOTE: Make sure you follow the [prerequisites](./README.md).

### Airsonic Advanced configuration

You will need to make sure Airsonic Advanced uses the correct headers for redirects, by setting the `server.forward-headers-strategy` property to `native` or `framework`.

`framework` is the recommended value, but you can set it to `native` if you want to use the Apache headers.

Note that in case you opt for using `native` it might be necessary to also set `X-Forwarded-Host` and/or `X-Forwarded-Port` as stated in [prerequisites](./README.md).

### Apache configuration

Create a new virtual host file:

```
sudo nano /etc/apache2/sites-available/airsonic.conf
```

Paste the following configuration in the virtual host file:

```
<VirtualHost *:80>
    ServerName example.com
    Redirect permanent / https://example.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName example.com

    SSLEngine On
    SSLCertificateFile cert.pem
    SSLCertificateKeyFile key.pem
    SSLProxyEngine on

    LogLevel warn
    
    ProxyPass         /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
    ProxyPassReverse  /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
    ProxyPass         /airsonic http://127.0.0.1:4040/airsonic
    ProxyPassReverse  /airsonic http://127.0.0.1:4040/airsonic
    RequestHeader     set       X-Forwarded-Proto "https"
</VirtualHost>
```

Alternatively, if you want to use an existing configuration, you can also paste the configuration below inside an existing `VirtualHost` block:

```
ProxyPass         /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
ProxyPassReverse  /airsonic/websocket ws://127.0.0.1:4040/airsonic/websocket
ProxyPass         /airsonic http://127.0.0.1:8080/airsonic
ProxyPassReverse  /airsonic http://127.0.0.1:8080/airsonic
RequestHeader     set       X-Forwarded-Proto "https"
```
You will need to make a couple of changes in the configuration file:

- Replace `example.com` with your own domain name.
- Be sure to set the right path to your `cert.pem` and `key.pem` files.
- Change `/airsonic` following your Airsonic context path.
- Change `http://127.0.0.1:4040/airsonic` following you Airsonic Advanced server location, port and path.

Activate the host:

```
sudo a2ensite airsonic.conf
```

Activate the following Apache modules:

```
sudo a2enmod proxy
sudo a2enmod proxy_http
sudo a2enmod proxy_wstunnel
sudo a2enmod ssl
sudo a2enmod headers
```

Restart the `apache2` service:

```
sudo systemctl restart apache2.service
```

## Content Security Policy

You may face some `Content-Security-Policy` issues. To fix this, add the following line to your Apache configuration:

```
<Location /airsonic>
    Header set Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' www.gstatic.com; img-src 'self' *.akamaized.net; style-src 'self' 'unsafe-inline' fonts.googleapis.com; font-src 'self' fonts.gstatic.com; frame-src 'self'; object-src 'none'"
</Location>
```
