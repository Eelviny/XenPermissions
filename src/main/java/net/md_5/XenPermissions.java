package net.md_5;

import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class XenPermissions extends JavaPlugin implements Listener
{

    private Config conf;
    private String url;

    @Override
    public void onEnable()
    {
        try
        {
            conf = new Config( this );
            url = "jdbc:mysql://" + conf.mysql_host + "/" + conf.mysql_database + "?user=" + conf.mysql_username + "&password=" + conf.mysql_password;

            Class.forName( "com.mysql.jdbc.Driver" );
            Connection con = DriverManager.getConnection( url );
            con.close();

            getServer().getPluginManager().registerEvents( this, this );
        } catch ( Exception ex )
        {
            getLogger().severe( "Could not enable: " + ex );
        }
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) throws SQLException
    {
        final String player = event.getName();

        Connection con = DriverManager.getConnection( url );
        PreparedStatement ps = con.prepareStatement( conf.getGroups );
        ps.setString( 1, player );
        ResultSet rs = ps.executeQuery();

        if ( rs.next() )
        {
            int primaryGroup = rs.getInt( 1 );
            String[] secondaryGroups = new String( rs.getBytes( 2 ) ).split( "," );

            final String primaryName = conf.groupMappings.get( Integer.toString( primaryGroup ) );
            final String[] secondaryNames = new String[ secondaryGroups.length ];
            for ( int i = 0; i < secondaryNames.length; i++ )
            {
                String name = conf.groupMappings.get( secondaryGroups[i] );
                secondaryNames[i] = name;
            }

            Futures.getUnchecked( getServer().getScheduler().callSyncMethod( this, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    if ( primaryName != null )
                    {
                        getServer().dispatchCommand( getServer().getConsoleSender(), MessageFormat.format( conf.clearCommand, player ) );
                        getServer().dispatchCommand( getServer().getConsoleSender(), MessageFormat.format( conf.setCommand, player, primaryName ) );
                    }

                    for ( String secondary : secondaryNames )
                    {
                        if ( secondary != null )
                        {
                            getServer().dispatchCommand( getServer().getConsoleSender(), MessageFormat.format( conf.addCommand, player, secondary ) );
                        }
                    }
                    return null;
                }
            } ) );
        }

        con.close();
    }

    private static class Config
    {

        private String mysql_host = "localhost";
        private String mysql_database = "xenforo";
        private String mysql_username = "user";
        private String mysql_password = "password";
        //
        private String getGroups = "SELECT user_group_id, secondary_group_ids FROM xf_user WHERE user_id = (SELECT user_id FROM xf_user_field_value WHERE field_id = 'Minecraft' AND username= ? LIMIT 1)";
        //
        private String clearCommand = "permissions player {0} purge";
        private String setCommand = "permissions player {0} setgroup {1}";
        private String addCommand = "permissions group {1} add {0}";
        //
        private Map<String, String> groupMappings = new HashMap<String, String>()
        {
            
            {
                put( "1", "Owner" );
                put( "2", "Admin" );
                put( "3", "Moderator" );
                put( "4", "Member" );
            }
        };

        private Config(Plugin plugin)
        {
            FileConfiguration conf = plugin.getConfig();
            for ( Field field : getClass().getDeclaredFields() )
            {
                String path = field.getName().replaceAll( "_", "." );
                try
                {
                    if ( conf.isSet( path ) )
                    {
                        if ( field.getType().isAssignableFrom( Map.class ) )
                        {
                            field.set( this, conf.getConfigurationSection( path ).getValues( true ) );
                        } else
                        {
                            field.set( this, conf.get( path ) );
                        }
                    } else
                    {
                        conf.set( path, field.get( this ) );
                    }
                } catch ( IllegalAccessException ex )
                {
                }
            }
            plugin.saveConfig();
        }
    }
}
