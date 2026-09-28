<head-bottom>
  <link rel="stylesheet" href="{{baseUrl}}/stylesheets/main.css">
</head-bottom>

<header sticky>
  <navbar type="dark">
    <a slot="brand" href="{{baseUrl}}/index.html" title="Home" class="navbar-brand">HotShop</a>
    <li><a highlight-on="sibling-or-child" href="{{baseUrl}}/docs/UserGuide.html" class="nav-link">User Guide</a></li>
    <li><a highlight-on="sibling-or-child" href="{{baseUrl}}/docs/DeveloperGuide.html" class="nav-link">Developer Guide</a></li>
    <li><a href="https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2" target="_blank" class="nav-link"><md>:fab-github:</md></a>
    </li>
    <li slot="right">
      <form class="navbar-form">
        <searchbar :data="searchData" placeholder="Search" :on-hit="searchCallback" menu-align-right></searchbar>
      </form>
    </li>
  </navbar>
</header>

<div id="flex-body">
  <nav id="site-nav">
    <div class="nav-component slim-scroll">
      <site-nav>
* [Home]({{ baseUrl }}/index.html)
* [User Guide]({{ baseUrl }}/docs/UserGuide.html) :expanded:
  * [Getting Started]({{ baseUrl }}/docs/userGuide/GettingStarted.html)
  * [Accounts and Interface]({{ baseUrl }}/docs/userGuide/AccountsAndInterface.html)
  * [Search and Listings]({{ baseUrl }}/docs/userGuide/SearchAndListings.html)
  * [Offers and Sales]({{ baseUrl }}/docs/userGuide/OffersAndSales.html)
  * [Conversations]({{ baseUrl }}/docs/userGuide/Conversations.html)
  * [Meetups]({{ baseUrl }}/docs/userGuide/Meetups.html)
  * [Marketplace Rules]({{ baseUrl }}/docs/userGuide/MarketplaceRules.html)
* [Developer Guide]({{ baseUrl }}/docs/DeveloperGuide.html) :expanded:
  * [Development Workflow]({{ baseUrl }}/docs/developerGuide/DevelopmentWorkflow.html)
  * [Architecture Overview]({{ baseUrl }}/docs/developerGuide/ArchitectureOverview.html)
  * [JavaFX UI]({{ baseUrl }}/docs/developerGuide/JavaFXUI.html)
  * [Service Worker]({{ baseUrl }}/docs/developerGuide/ServiceWorker.html)
  * [Account Service and Local Persistence]({{ baseUrl }}/docs/developerGuide/AccountServiceAndLocalPersistence.html)
  * [Listing Service]({{ baseUrl }}/docs/developerGuide/ListingService.html)
  * [Offer Service]({{ baseUrl }}/docs/developerGuide/OfferService.html)
  * [Chat Service]({{ baseUrl }}/docs/developerGuide/ChatService.html)
  * [Transaction Service]({{ baseUrl }}/docs/developerGuide/TransactionService.html)
  * [Meetup Service]({{ baseUrl }}/docs/developerGuide/MeetupService.html)
  * [Acknowledgements]({{ baseUrl }}/docs/developerGuide/Acknowledgements.html)
  * [Future Work]({{ baseUrl }}/docs/developerGuide/FutureWork.html)
      </site-nav>
    </div>
  </nav>
  <div id="content-wrapper">
    {{ content }}
  </div>
  <nav id="page-nav">
    <div class="nav-component slim-scroll">
      <page-nav />
    </div>
  </nav>
  <scroll-top-button></scroll-top-button>
</div>

<footer>
  <!-- Support MarkBind by including a link to us on your landing page! -->
  <div class="text-center">
    <small>[<md>**Powered by**</md> <img src="https://markbind.org/favicon.ico" width="30"> {{MarkBind}}]</small>
  </div>
</footer>
