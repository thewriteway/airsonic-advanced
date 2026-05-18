# First start guide

This guide explain how to handle your first start with Airsonic.

## Change Airsonic process user

We recommand not to run Airsonic using the root user.

To change this you can create a dedicated user that will run Airsonic.

> NOTE: Please verify any permissions after you changed the process user.

## Set up user accounts

Airsonic creates an administrator account automatically on first startup. The default username is `admin`, but the password is not fixed unless you set it explicitly before startup.

If you want to specify the initial admin password, set the `AIRSONIC_ADMIN_PASSWORD` environment variable before starting Airsonic. Otherwise a strong random password will be generated and written to the startup log.

Go to Settings > Users to change this password.

In the same page you can also create new user accounts and specify which operations they are allowed to perform.

## Setting up media folders

You must tell Airsonic where you keep your music and videos.

Select `Settings` > `Media folders` to add folders. (If you don’t see this option you must first log on to Airsonic with a user that has administrative privileges, for instance the admin user).

Also note that Airsonic will organize your music according to how they are organized on your disk. Unlike many other music applications, Airsonic does not organize the music according to the tag information embedded in the files. (It does, however, also read the tags for presentation and search purposes.)

Consequently, it’s recommended that the music folders you add to Airsonic are organized in an **“artist/album/song”** manner. There are music managers, like MediaMonkey, that can help you achieve this.
