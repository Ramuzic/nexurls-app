# NexURLs on Google Play

Everything for the store listing, ready to paste into Play Console.
Images in this folder: icon-512.png (app icon), feature-graphic.png (1024 x 500),
screenshots/ (6 phone screenshots, 2:1).

## App name (30 characters max)
NexURLs

## Short description (80 characters max)
Tap once and land somewhere new on the internet. Save, share and discover sites.

## Full description
There's always another URL.

NexURLs takes you somewhere new on the internet with one tap. Real websites, picked by real people. No algorithm feeding you the same five apps.

Tap Next and see where you land. Like it? Save it. Don't? Tap again. There are thousands of places waiting.

What you can do
- Tap Next to jump to a random website people have shared
- Save the good ones and come back to them any time
- Browse Fresh, Most loved and Trends to see what people are finding
- Share your own site, project or favorite find for free and get it in front of new people
- Plan a launch day and get the spotlight for 24 hours
- Watch Reels, short video reactions to links made by members
- Join the community, follow people and chat with other members
- Play daily games like This or That and Guess the site

Made something?
NexURLs is a free way to get eyes on your website, shop, blog or side project. Submit it, and people who are out exploring will find it.

Invite a friend
You both get NexCredits once they've explored 10 links.

NexURLs is free to use. Sign in to save links, post, chat and get notified when someone replies.

## Category
Social (or Entertainment)

## Contact details
Website: https://nexurls.com
Email: the support email you want public
Privacy policy: https://nexurls.com/privacy/ (check the exact address of your Privacy page)

## Content rating questionnaire (Play Console > Policy > App content)
Answer for what the app really has: members can post links, text, images and videos,
chat with each other, and there is a mature-links filter. Expect a Teen style rating
because of user posts and chat. Say yes to "users can interact" and "shares user content".

## Data safety form
The app collects, for signed-in members: name, email, user ID, posts and messages,
photos and videos they upload, and a push notification ID (OneSignal). Data is sent over
HTTPS. Members can ask for their account to be deleted (have a delete-account page or
email ready; Google asks for a link).

## Things to check before going live
1. Payments: Google Play has its own rules for selling digital things (Pro, NexCredits)
   inside an Android app. The iPhone app uses Apple in-app purchases; the Android app
   currently sends people to the website checkout. Check Google's current payments
   policy for your country before the Android app shows Pro or credit purchases, or hide
   them in the Android app for the first release.
2. Push on Android: Firebase project with an Android app com.nexurls.app, then its
   service account key uploaded in OneSignal > nexurls > Settings > Google Android (FCM).
3. Account deletion link (required by Google for apps with sign-up).
