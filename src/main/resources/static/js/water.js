document.addEventListener( "DOMContentLoaded", () => {
    initChart()
    initModal()
} )

/* =========================================================
   水分摂取量推移グラフ
   共通の HealthChart を使用する
   ========================================================= */
   function initChart() {
       const isSearching = Boolean(
           document.getElementById("startDateInput")?.value ||
           document.getElementById("endDateInput")?.value
       );

       const chart = HealthChart.create({
           canvasId: "waterChart",
           data: window.waterChartData,
           unit: "ml",
           type: "bar",
           days: 7,
           isSearching: isSearching,
       });

       const wrapper = document.getElementById("chartWrapper");
       const chartUrl = wrapper?.closest(".chart-card")?.dataset.chartUrl;
       const from = window.waterChartData?.from;
       const to = window.waterChartData?.to;

       if (chart && wrapper && chartUrl && from && to) {
           initChartSwipe({ chart, wrapperEl: wrapper, chartUrl, initialFrom: from, initialTo: to });
       }
   }

/* =========================================================
   水分記録モーダル（新規登録 / 編集）
   ========================================================= */
function initModal() {
    let currentMode = "create"
    const overlay = document.getElementById( "waterModalOverlay" )
    const openBtns = [
        document.getElementById( "openAddModalBtn" ),
        document.getElementById( "openAddModalBtnEmpty" ),
        document.getElementById( "openAddModalBtnFiltered" ),
    ].filter( Boolean )

    const closeBtn = document.getElementById( "closeModalBtn" )
    const cancelBtn = document.getElementById( "cancelModalBtn" )
    const modalTitle = document.getElementById( "waterModalTitle" )
    const form = document.getElementById( "waterForm" )

    const recordId = document.getElementById( "recordId" )
    const dateInput = document.getElementById( "recordedDate" )
    const timeInput = document.getElementById( "recordedTime" )
    const drinkTypeInput = document.getElementById( "drinkType" )
    const amountInput = document.getElementById( "amountMl" )
    const memoInput = document.getElementById( "memo" )

    if ( !overlay || !form ) {
        return
    }

    // 現在時刻を HH:mm 形式で取得する
    function currentTimeLocal() {
        const date = new Date()
        const hours = String( date.getHours() ).padStart( 2, "0" )
        const minutes = String( date.getMinutes() ).padStart( 2, "0" )
        return `${hours}:${minutes}`
    }

    // 編集時の時刻を HH:mm 形式に統一する
    function normalizeTime( value ) {
        if ( !value ) {
            return ""
        }
        return value.substring( 0, 5 )
    }

    /* =====================================================
         モーダルを開く
         ===================================================== */
    function openModal( {
        mode = "create",
        id = "",
        date = "",
        time = "",
        drinkType = "",
        amount = "",
        memo = ""
    } = {} ) {

        currentMode = mode

        modalTitle.textContent =
            mode === "edit"
                ? "水分記録を編集する"
                : "水分を記録する"

        recordId.value = id
        dateInput.value = date || todayIso()

        timeInput.value =
            mode === "create"
                ? currentTimeLocal()
                : normalizeTime( time )

        drinkTypeInput.value = drinkType
        amountInput.value = amount
        memoInput.value = memo

        clearAllErrors()
        overlay.classList.add( "is-open" )
        dateInput.focus()
    }

    /* =====================================================
         モーダルを閉じる
         ===================================================== */
    function closeModal() {
        overlay.classList.remove( "is-open" )
    }

    /* =====================================================
         新規登録ボタン
         ===================================================== */
    openBtns.forEach( ( btn ) => {
        btn.addEventListener( "click", () => {
            openModal( {
                mode: "create",
                date: btn.dataset.date || "",
            } )
        } )
    } )

    if ( closeBtn ) {
        closeBtn.addEventListener( "click", closeModal )
    }

    if ( cancelBtn ) {
        cancelBtn.addEventListener( "click", closeModal )
    }

    /* =====================================================
         モーダル外をクリックした場合
         ===================================================== */
    overlay.addEventListener( "click", ( event ) => {
        if ( event.target === overlay ) {
            closeModal()
        }
    } )

    /* =====================================================
         Escapeキーで閉じる
         ===================================================== */
    document.addEventListener( "keydown", ( event ) => {
        if ( event.key === "Escape" && overlay.classList.contains( "is-open" ) ) {
            closeModal()
        }
    } )

    /* =====================================================
         編集ボタン
         ===================================================== */
    document.querySelectorAll( ".edit-btn" ).forEach( ( btn ) => {
        btn.addEventListener( "click", () => {
            openModal( {
                mode: "edit",
                id: btn.dataset.id || "",
                date: btn.dataset.date || "",
                time: normalizeTime( btn.dataset.time ),
                drinkType: btn.dataset.drinkType || "",
                amount: btn.dataset.amount || "",
                memo: btn.dataset.memo || "",
            } )
        } )
    } );

    /* =====================================================
         入力時にエラー表示を解除
         ===================================================== */
    [ dateInput, drinkTypeInput, amountInput ].forEach( ( input ) => {
        if ( !input ) {
            return
        }
        input.addEventListener( "input", () => {
            clearError( input.id )
        } )
        input.addEventListener( "change", () => {
            clearError( input.id )
        } )
    } )

    /* =====================================================
         日付チェック
         ===================================================== */
    function validateDate() {
        if ( !dateInput.value ) {
            showError( "recordedDate", "日付を選択してください" )
            return false
        }
        const selectedDate = new Date( dateInput.value + "T00:00:00" )
        const today = new Date()
        today.setHours( 23, 59, 59, 999 )
        if ( selectedDate > today ) {
            showError( "recordedDate", "未来の日付は選択できません" )
            return false
        }
        clearError( "recordedDate" )
        return true
    }

    /* =====================================================
         飲み物チェック
         ===================================================== */
    function validateDrinkType() {
        if ( !drinkTypeInput.value ) {
            showError( "drinkType", "飲み物を選択してください" )
            return false
        }
        clearError( "drinkType" )
        return true
    }

    /* =====================================================
         水分量チェック
         ===================================================== */
    function validateAmount() {
        const value = amountInput.value
        if ( !value ) {
            showError( "amountMl", "水分量を入力してください" )
            return false
        }
        const amount = Number( value )
        if ( Number.isNaN( amount ) || amount < 1 || amount > 5000 ) {
            showError( "amountMl", "水分量は1〜5000mlの範囲で入力してください" )
            return false
        }
        clearError( "amountMl" )
        return true
    }

    /* =====================================================
         送信時バリデーション
         ===================================================== */
    form.addEventListener( "submit", ( event ) => {
        const isDateValid = validateDate()
        const isDrinkTypeValid = validateDrinkType()
        const isAmountValid = validateAmount()

        if (
            !isDateValid ||
            !isDrinkTypeValid ||
            !isAmountValid
        ) {
            event.preventDefault()
            return
        }

        // 新規登録の場合、保存した時点の現在時刻を記録する
        if ( currentMode === "create" ) {
            timeInput.value = currentTimeLocal()
        }
    } )

    /* =====================================================
         エラー表示
         ===================================================== */
    function showError( inputId, message ) {
        const input = document.getElementById( inputId )
        const errorBox = document.getElementById( inputId + "Error" )
        if ( !input || !errorBox ) {
            return
        }
        input.classList.add( "invalid" )
        const span = errorBox.querySelector( "span" )
        if ( span ) {
            span.textContent = message
        }
        errorBox.classList.add( "show" )
    }

    /* =====================================================
         エラー解除
         ===================================================== */
    function clearError( inputId ) {
        const input = document.getElementById( inputId )
        const errorBox = document.getElementById( inputId + "Error" )
        if ( !input || !errorBox ) {
            return
        }
        input.classList.remove( "invalid" )
        const span = errorBox.querySelector( "span" )
        if ( span ) {
            span.textContent = ""
        }
        errorBox.classList.remove( "show" )
    }

    /* =====================================================
         全エラー解除
         ===================================================== */
    function clearAllErrors() {
        [ "recordedDate", "drinkType", "amountMl" ].forEach( clearError )
    }

    /* =====================================================
         現在日付を yyyy-MM-dd 形式で取得
         ===================================================== */
    function todayIso() {
        const date = new Date()
        const year = date.getFullYear()
        const month = String( date.getMonth() + 1 ).padStart( 2, "0" )
        const day = String( date.getDate() ).padStart( 2, "0" )
        return `${year}-${month}-${day}`
    }

    /* =====================================================
         現在時刻を HH:mm 形式で取得
         ===================================================== */
    function currentTimeLocal() {
        const date = new Date()
        const hours = String( date.getHours() ).padStart( 2, "0" )
        const minutes = String( date.getMinutes() ).padStart( 2, "0" )
        return `${hours}:${minutes}`
    }

    /* =====================================================
         編集時の時刻を HH:mm 形式に統一
         ===================================================== */
    function normalizeTime( value ) {
        if ( !value ) {
            return ""
        }
        return value.substring( 0, 5 )
    }
}
