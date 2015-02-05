package de.berlios.vch.parser.rss;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.jdom.Element;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import com.sun.syndication.feed.synd.SyndEnclosure;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import de.berlios.vch.config.ConfigService;
import de.berlios.vch.i18n.ResourceBundleLoader;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.rss.RssParser;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;

@Component
@Provides
public class RssFeedParser implements IWebParser, ResourceBundleProvider {

    @Requires
    private ConfigService cs;

    private Preferences prefs;

    @Requires
    private LogService logger;

    private BundleContext ctx;

    private ResourceBundle resourceBundle;

    private ServiceRegistration<IWebMenuEntry> menuReg;

    public RssFeedParser(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String getId() {
        return RssFeedParser.class.getName();
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage root = new OverviewPage();
        root.setParser(getId());
        root.setTitle("RSS Feeds");
        root.setUri(new URI("vchpage://localhost/" + getId()));
        for (Feed feed : getFeeds()) {
            OverviewPage page = new OverviewPage();
            page.setParser(getId());
            page.setTitle(feed.getTitle());
            page.setUri(new URI(feed.getUri()));
            root.getPages().add(page);
        }
        return root;
    }

    @Override
    public String getTitle() {
        return "RSS Feeds";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof VideoPage) {
            return page;
        } else {
            String feedUri = page.getUri().toString();
            logger.log(LogService.LOG_INFO, "Parsing rss feed " + feedUri);
            // String rss = HttpUtils.get(feedUri, null, "UTF-8");
            SyndFeed feed = RssParser.parseUri(feedUri);
            feed.setLink(feedUri);
            feed.setTitle(page.getTitle());

            OverviewPage feedPage = new OverviewPage();
            feedPage.setParser(getId());
            feedPage.setTitle(page.getTitle());
            feedPage.setUri(page.getUri());
            for (Iterator<?> iterator = feed.getEntries().iterator(); iterator.hasNext();) {
                SyndEntry entry = (SyndEntry) iterator.next();
                VideoPage video = new VideoPage();
                video.setParser(getId());
                video.setTitle(entry.getTitle());
                video.setDescription(entry.getDescription().getValue());
                Calendar pubCal = Calendar.getInstance();
                pubCal.setTime(entry.getPublishedDate());
                video.setPublishDate(pubCal);
                video.setVideoUri(new URI(((SyndEnclosure) entry.getEnclosures().get(0)).getUrl()));
                if (entry.getLink() != null) {
                    video.setUri(new URI(entry.getLink()));
                } else {
                    video.setUri(video.getVideoUri());
                }

                // look, if we have a duration in the foreign markup
                @SuppressWarnings("unchecked")
                List<Element> fm = (List<Element>) entry.getForeignMarkup();
                for (Element element : fm) {
                    if ("duration".equals(element.getName())) {
                        try {
                            video.setDuration(Long.parseLong(element.getText()));
                        } catch (Exception e) {
                        }
                    }
                }

                // add the entry to the overview page
                feedPage.getPages().add(video);
            }
            return feedPage;
        }
    }

    @Validate
    public void start() {
        prefs = cs.getUserPreferences(ctx.getBundle().getSymbolicName());
        registerServlet();
    }

    private void registerServlet() {
        try {
            // register web interface menu
            IWebMenuEntry menu = new WebMenuEntry(getResourceBundle().getString("I18N_BROWSE"));
            menu.setPreferredPosition(Integer.MIN_VALUE + 1);
            menu.setLinkUri("#");
            SortedSet<IWebMenuEntry> childs = new TreeSet<IWebMenuEntry>();
            IWebMenuEntry entry = new WebMenuEntry();
            entry.setTitle(getTitle());
            entry.setLinkUri("/parser?id=" + getClass().getName());
            childs.add(entry);
            menu.setChilds(childs);
            childs = new TreeSet<IWebMenuEntry>();
            IWebMenuEntry open = new WebMenuEntry();
            open.setTitle(getResourceBundle().getString("I18N_OPEN"));
            open.setLinkUri(entry.getLinkUri());
            childs.add(open);
            entry.setChilds(childs);
            menuReg = ctx.registerService(IWebMenuEntry.class, menu, null);
        } catch (Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't register rss parser config servlet", e);
        }
    }

    @Invalidate
    public void stop() {
        prefs = null;

        // unregister web menu
        if (menuReg != null) {
            menuReg.unregister();
        }
    }

    public List<Feed> getFeeds() {
        List<Feed> feeds = new ArrayList<Feed>();
        try {
            Preferences persitentFeeds = prefs.node("feeds");
            String[] feedIds = persitentFeeds.childrenNames();
            for (String id : feedIds) {
                Preferences feed = persitentFeeds.node(id);
                String title = feed.get("title", "N/A");
                String uri = feed.get("uri", "");
                feeds.add(new Feed(id, title, uri));
            }
        } catch (BackingStoreException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't load preferences", e);
        }
        Collections.sort(feeds);
        return feeds;
    }

    @Override
    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            try {
                logger.log(LogService.LOG_DEBUG, "Loading resource bundle for " + getClass().getSimpleName());
                resourceBundle = ResourceBundleLoader.load(ctx, Locale.getDefault());
            } catch (IOException e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load resource bundle", e);
            }
        }
        return resourceBundle;
    }
}
