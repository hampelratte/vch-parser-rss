package de.berlios.vch.parser.rss;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;

import com.sun.syndication.feed.synd.SyndFeed;

import de.berlios.vch.config.ConfigService;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.rss.VdrWikiSuggestions.Feed;
import de.berlios.vch.parser.rss.VdrWikiSuggestions.Group;
import de.berlios.vch.rss.RssParser;
import de.berlios.vch.web.NotifyMessage;
import de.berlios.vch.web.NotifyMessage.TYPE;
import de.berlios.vch.web.TemplateLoader;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;
import de.berlios.vch.web.servlets.VchHttpServlet;

@Component
public class ConfigServlet extends VchHttpServlet {

    public static String PATH = "/config/parser/rss";

    @Requires
    private ConfigService cs;

    private Preferences prefs;

    @Requires
    private LogService logger;

    @Requires(filter = "(instance.name=vch.parser.rss)")
    private ResourceBundleProvider rbp;

    @Requires
    private TemplateLoader templateLoader;

    @Requires
    private HttpService httpService;

    @Requires(filter = "(instance.name=vch.parser.rss)")
    private IWebParser parser;

    private BundleContext ctx;

    private ServiceRegistration<IWebMenuEntry> menuReg;

    public ConfigServlet(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> params = new HashMap<String, Object>();

        if (req.getParameter("add_feed") != null) {
            String feedUri = req.getParameter("feed");
            addFeed(feedUri, req);
        } else if (req.getParameter("add_feeds") != null) {
            String[] feeds = req.getParameterValues("feeds");
            if (feeds != null) {
                for (String feedUri : feeds) {
                    addFeed(feedUri, req);
                }
            }
        } else if (req.getParameter("remove_feeds") != null) {
            String[] feeds = req.getParameterValues("feeds");
            if (feeds != null) {
                for (String id : feeds) {
                    removeFeed(id);
                }
            }
        } else if (req.getParameter("get_suggestions") != null) {
            try {
                List<Group> groups = VdrWikiSuggestions.loadSuggestions();
                resp.setContentType("application/json; charset=utf-8");
                resp.getWriter().write(toJSON(groups));
            } catch (Exception e) {
                String msg = rbp.getResourceBundle().getString("I18N_ERROR_LOAD_SUGGESTIONS");
                logger.log(LogService.LOG_ERROR, msg, e);
                addNotify(req, new NotifyMessage(TYPE.ERROR, msg, e));
            }
            return;
        }

        params.put("TITLE", rbp.getResourceBundle().getString("I18N_RSS_CONFIG"));
        params.put("SERVLET_URI", req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + req.getServletPath());
        params.put("FEEDS", getFeeds());
        params.put("ACTION", PATH);
        params.put("NOTIFY_MESSAGES", getNotifyMessages(req));

        String page = templateLoader.loadTemplate("configRss.ftl", params);
        resp.getWriter().print(page);
    }

    private void addFeed(String feedUri, HttpServletRequest req) {
        try {
            SyndFeed feed = RssParser.parseUri(feedUri);
            addFeed(feed.getTitle(), feedUri);
            addNotify(req, new NotifyMessage(TYPE.INFO, rbp.getResourceBundle().getString("I18N_FEED_ADDED")));
        } catch (Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't parse feed", e);
            addNotify(req, new NotifyMessage(TYPE.ERROR, rbp.getResourceBundle().getString("I18N_ERROR_COULDNT_PARSE_FEED"), e));
        }
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        get(req, resp);
    }

    private String toJSON(List<Group> groups) {
        if (!groups.isEmpty()) {
            String json = "[";
            for (Iterator<Group> iterator = groups.iterator(); iterator.hasNext();) {
                Group group = iterator.next();
                json += groupToJSON(group);
                if (iterator.hasNext()) {
                    json += ", ";
                }
            }
            return json += "]";
        } else {
            return "[]";
        }
    }

    private String groupToJSON(Group group) {
        Map<String, Object> object = new HashMap<String, Object>();
        object.put("title", group.title);
        object.put("feeds", feedsToJSON(group.feeds));
        return new JSONObject(object).toString();
    }

    private List<JSONObject> feedsToJSON(List<Feed> feeds) {
        List<JSONObject> result = new ArrayList<JSONObject>();
        if (!feeds.isEmpty()) {
            for (Iterator<Feed> iterator = feeds.iterator(); iterator.hasNext();) {
                Feed feed = iterator.next();
                Map<String, String> feedMap = new HashMap<String, String>();
                feedMap.put("title", feed.title);
                feedMap.put("uri", feed.uri);
                result.add(new JSONObject(feedMap));
            }
        }
        return result;
    }

    @Validate
    public void start() throws ServletException, NamespaceException {
        prefs = cs.getUserPreferences(ctx.getBundle().getSymbolicName());

        // register the servlet
        httpService.registerServlet(ConfigServlet.PATH, this, null, null);

        registerMenu();
    }

    @Invalidate
    public void stop() {
        // unregister the servlet
        httpService.unregister(ConfigServlet.PATH);

        // unregister the menu
        menuReg.unregister();
    }

    private void registerMenu() {
        // register web interface menu
        IWebMenuEntry menu = new WebMenuEntry(rbp.getResourceBundle().getString("I18N_BROWSE"));
        menu.setPreferredPosition(Integer.MIN_VALUE + 1);
        menu.setLinkUri("#");
        SortedSet<IWebMenuEntry> childs = new TreeSet<IWebMenuEntry>();
        IWebMenuEntry entry = new WebMenuEntry();
        entry.setTitle(parser.getTitle());
        entry.setLinkUri("/parser?id=" + getClass().getName());
        childs.add(entry);
        menu.setChilds(childs);
        childs = new TreeSet<IWebMenuEntry>();
        IWebMenuEntry config = new WebMenuEntry();
        config.setTitle(rbp.getResourceBundle().getString("I18N_CONFIGURATION"));
        config.setLinkUri(ConfigServlet.PATH);
        config.setPreferredPosition(Integer.MAX_VALUE);
        childs.add(config);
        entry.setChilds(childs);
        menuReg = ctx.registerService(IWebMenuEntry.class, menu, null);
    }

    public void addFeed(String title, String uri) {
        Preferences feeds = prefs.node("feeds");
        String id = UUID.randomUUID().toString();
        Preferences feed = feeds.node(id);
        feed.put("title", title);
        feed.put("uri", uri);
    }

    public void removeFeed(String id) {
        Preferences feeds = prefs.node("feeds");
        Preferences feed = feeds.node(id);
        try {
            feed.removeNode();
        } catch (BackingStoreException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't remove feed", e);
        }
    }

    public List<de.berlios.vch.parser.rss.Feed> getFeeds() {
        List<de.berlios.vch.parser.rss.Feed> feeds = new ArrayList<de.berlios.vch.parser.rss.Feed>();
        try {
            Preferences persitentFeeds = prefs.node("feeds");
            String[] feedIds = persitentFeeds.childrenNames();
            for (String id : feedIds) {
                Preferences feed = persitentFeeds.node(id);
                String title = feed.get("title", "N/A");
                String uri = feed.get("uri", "");
                feeds.add(new de.berlios.vch.parser.rss.Feed(id, title, uri));
            }
        } catch (BackingStoreException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't load preferences", e);
        }
        Collections.sort(feeds);
        return feeds;
    }
}
