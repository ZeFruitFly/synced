//
// $Id$

package com.threerings.msoy.apps.server;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.threerings.msoy.apps.gwt.AppInfo;
import com.threerings.msoy.apps.gwt.AppService;
import com.threerings.msoy.apps.gwt.FacebookNotification;
import com.threerings.msoy.apps.gwt.FacebookNotificationStatus;
import com.threerings.msoy.apps.server.persist.AppInfoRecord;
import com.threerings.msoy.apps.server.persist.AppRepository;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.facebook.gwt.FacebookInfo;
import com.threerings.msoy.facebook.gwt.FacebookTemplate;
import com.threerings.msoy.facebook.gwt.FeedThumbnail;
import com.threerings.msoy.facebook.gwt.KontagentInfo;
import com.threerings.msoy.facebook.server.FacebookLogic;
import com.threerings.msoy.facebook.server.persist.FacebookNotificationRecord;
import com.threerings.msoy.facebook.server.persist.FacebookNotificationStatusRecord;
import com.threerings.msoy.facebook.server.persist.FacebookRepository;
import com.threerings.msoy.facebook.server.persist.FacebookTemplateRecord;
import com.threerings.msoy.facebook.server.persist.FeedThumbnailRecord;
import com.threerings.msoy.facebook.server.persist.KontagentInfoRecord;
import com.threerings.msoy.item.data.ItemCodes;
import com.threerings.msoy.web.gwt.ClientMode;
import com.threerings.msoy.web.gwt.ServiceException;
import com.threerings.msoy.web.server.MsoyServiceServlet;

/**
 * Implements the application service.
 */
public class AppServlet extends MsoyServiceServlet
    implements AppService
{
    @Override // from AppService
    public List<AppInfo> getApps ()
        throws ServiceException
    {
        requireAdminUser();
        return Lists.newArrayList(Lists.transform(_appRepo.loadApps(), AppInfoRecord.TO_APP_INFO));
    }

    @Override // from AppService
    public int createApp (String name)
        throws ServiceException
    {
        requireAdminUser();
        AppInfoRecord app = new AppInfoRecord();
        app.name = name;
        app.clientMode = ClientMode.UNSPECIFIED;
        _appRepo.createApp(app);
        return app.appId;
    }

    @Override // from AppService
    public AppData getAppData (int appId)
        throws ServiceException
    {
        AppData data = new AppData();
        data.info = requireApp(appId).toAppInfo();
        data.facebook = _facebookRepo.loadAppFacebookInfo(appId);
        KontagentInfoRecord kinfo = _facebookRepo.loadKontagentInfo(appId);
        data.kontagent = kinfo==null ? new KontagentInfo("", "") : kinfo.toKontagentInfo();
        return data;
    }

    @Override // from AppService
    public void deleteApp (int appId)
        throws ServiceException
    {
        _appRepo.deleteApp(requireApp(appId).appId);
    }

    @Override // from AppService
    public void updateAppInfo (AppInfo info)
        throws ServiceException
    {
        AppInfoRecord updated = requireApp(info.appId);
        updated.update(info);
        _appRepo.updateAppInfo(updated);
    }

    @Override // from AppService
    public void updateFacebookInfo (FacebookInfo info)
        throws ServiceException
    {
        requireApp(info.appId);
        _facebookRepo.updateFacebookInfo(info);
    }

    @Override // from AppService
    public void deleteNotification (int appId, String id)
        throws ServiceException
    {
        requireApp(appId);
        FacebookNotificationRecord notif = _facebookRepo.loadNotification(appId, id);
        if (notif == null) {
            throw new ServiceException("e.notification_cannot_be_deleted");
        }
        _facebookRepo.deleteNotification(appId, id);
    }

    @Override // from AppService
    public List<FacebookNotification> loadNotifications (int appId)
        throws ServiceException
    {
        requireApp(appId);
        List<FacebookNotification> notifs = Lists.newArrayList();
        for (FacebookNotificationRecord notif : _facebookRepo.loadNotifications(appId)) {
            notifs.add(notif.toNotification());
        }
        return notifs;
    }

    @Override // from AppService
    public void saveNotification (int appId, FacebookNotification notif)
        throws ServiceException
    {
        requireApp(appId);
        _facebookRepo.storeNotification(appId, notif.id, notif.text);
    }

    @Override // from AppService
    public void scheduleNotification (int appId, String id, int delay)
        throws ServiceException
    {
        requireApp(appId);
        // TODO: _facebookLogic.scheduleNotification(appId, id, delay);
        throw new ServiceException(MsoyCodes.INTERNAL_ERROR);
    }

    @Override // from AppService
    public List<FacebookNotificationStatus> loadNotificationsStatus (int appId)
        throws ServiceException
    {
        requireApp(appId);
        List<FacebookNotificationStatus> statusList = Lists.newArrayList();
        for (FacebookNotificationStatusRecord rec : _facebookRepo.loadNotificationStatus(appId)) {
            statusList.add(rec.toStatus());
        }
        return statusList;
    }

    @Override // from AppService
    public List<FacebookTemplate> loadTemplates (int appId)
        throws ServiceException
    {
        requireApp(appId);
        List<FacebookTemplate> result = Lists.newArrayList(
            Iterables.transform(_facebookRepo.loadTemplates(appId),
                new Function<FacebookTemplateRecord, FacebookTemplate>() {
                    public FacebookTemplate apply (FacebookTemplateRecord in) {
                        return in.toTemplate();
                    }
                }));
        Collections.sort(result);
        return result;
    }

    @Override // from AppService
    public void updateTemplates (
        int appId, Set<FacebookTemplate> templates, Set<FacebookTemplate> removed)
        throws ServiceException
    {
        requireApp(appId);
        for (FacebookTemplate templ : removed) {
            _facebookRepo.deleteTemplate(appId, templ.code, templ.variant);
        }
        for (FacebookTemplate templ : templates) {
            _facebookRepo.storeTemplate(new FacebookTemplateRecord(appId, templ));
        }
    }

    @Override // from AppService
    public List<FeedThumbnail> loadThumbnails (int appId)
        throws ServiceException
    {
        requireApp(appId);
        return Lists.newArrayList(Lists.transform(
            _facebookRepo.loadAppThumbnails(appId), FeedThumbnailRecord.TO_THUMBNAIL));
    }

    @Override // from AppService
    public void updateThumbnails (final int appId, List<FeedThumbnail> thumbnails)
        throws ServiceException
    {
        requireApp(appId);
        _facebookRepo.saveAppThumbnails(appId, Lists.transform(thumbnails,
            new Function<FeedThumbnail, FeedThumbnailRecord>() {
            public FeedThumbnailRecord apply (FeedThumbnail thumb) {
                return FeedThumbnailRecord.forApp(appId, thumb);
            }
        }));
    }

    @Override // from AppService
    public void updateKontagentInfo (int appId, KontagentInfo kinfo)
        throws ServiceException
    {
        requireApp(appId);
        _facebookRepo.saveKontagentInfo(new KontagentInfoRecord(appId, kinfo));
    }

    protected AppInfoRecord requireApp (int id)
        throws ServiceException
    {
        requireAdminUser();
        AppInfoRecord app = _appRepo.loadAppInfo(id);
        if (app == null) {
            throw new ServiceException(ItemCodes.E_NO_SUCH_ITEM);
        }
        return app;
    }

    // dependencies
    @Inject AppRepository _appRepo;
    @Inject FacebookLogic _facebookLogic;
    @Inject FacebookRepository _facebookRepo;
}
