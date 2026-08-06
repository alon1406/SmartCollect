/* =============================================
   Core config — system + API constants.
   Loaded first; everything else reads these globals.
   ============================================= */

const SYSTEM_ID = 'smartcollect';
const API_BASE  = '/ambient-invisible-intelligence';
const API_VER   = '1.4';

// List endpoints are paginated (default size 20); we request a large page to
// keep the "load all, filter client-side" behaviour.
const PAGE_SIZE = 1000;
