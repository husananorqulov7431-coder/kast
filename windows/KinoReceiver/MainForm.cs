using System;
using System.Drawing;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows.Forms;
using Microsoft.Web.WebView2.WinForms;

namespace KinoReceiver
{
    public class MainForm : Form
    {
        private WebView2 webView = null!;
        private Panel topBar = null!;
        private Label lblStatus = null!;
        private Button btnReconnect = null!;
        private Button btnFullscreen = null!;
        private System.Windows.Forms.Timer cursorTimer = null!;
        private Point lastMousePosition;
        private bool isFullscreen = false;
        private FormWindowState prevWindowState;
        private FormBorderStyle prevBorderStyle;
        private string phoneIp = "172.30.121.120";

        public MainForm()
        {
            InitializeComponent();
            SetupCursorHiding();
        }

        private void InitializeComponent()
        {
            this.Text = "🎬 KinoReceiver v3.0 - 16:9 Cinema Player";
            this.Size = new Size(1280, 750);
            this.MinimumSize = new Size(800, 480);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.Black;
            this.KeyPreview = true;

            phoneIp = DetectGatewayIp();

            // Top Bar
            topBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                BackColor = Color.FromArgb(20, 20, 20),
                Padding = new Padding(12, 6, 12, 6)
            };

            lblStatus = new Label
            {
                Text = $"🟢 Telefon bilan aloqa faol ({phoneIp})",
                ForeColor = Color.FromArgb(0, 230, 118),
                AutoSize = true,
                Location = new Point(14, 12),
                Font = new Font("Segoe UI", 10, FontStyle.Bold)
            };

            btnReconnect = new Button
            {
                Text = "🔄 Qayta Ulanish",
                Location = new Point(340, 8),
                Width = 140,
                Height = 28,
                BackColor = Color.FromArgb(35, 35, 35),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9),
                Cursor = Cursors.Hand
            };
            btnReconnect.FlatAppearance.BorderSize = 0;
            btnReconnect.Click += (s, e) =>
            {
                phoneIp = DetectGatewayIp();
                lblStatus.Text = $"🟢 Ulanmoqda ({phoneIp})...";
                webView.Source = new Uri($"http://{phoneIp}:8080/");
            };

            btnFullscreen = new Button
            {
                Text = "⛶ To'liq Ekran (F11)",
                Dock = DockStyle.Right,
                Width = 160,
                BackColor = Color.FromArgb(30, 136, 229),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnFullscreen.FlatAppearance.BorderSize = 0;
            btnFullscreen.Click += (s, e) => ToggleFullscreen();

            topBar.Controls.Add(lblStatus);
            topBar.Controls.Add(btnReconnect);
            topBar.Controls.Add(btnFullscreen);

            webView = new WebView2
            {
                Dock = DockStyle.Fill
            };

            this.Controls.Add(webView);
            this.Controls.Add(topBar);

            this.KeyDown += MainForm_KeyDown;
        }

        protected override async void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            try
            {
                await webView.EnsureCoreWebView2Async();
                phoneIp = DetectGatewayIp();
                lblStatus.Text = $"🟢 Ulanmoqda ({phoneIp})...";
                webView.Source = new Uri($"http://{phoneIp}:8080/");
            }
            catch (Exception ex)
            {
                MessageBox.Show("WebView2 yuklanishida xatolik: " + ex.Message, "Xatolik", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private string DetectGatewayIp()
        {
            try
            {
                foreach (var netInterface in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (netInterface.OperationalStatus == OperationalStatus.Up &&
                        (netInterface.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ||
                         netInterface.NetworkInterfaceType == NetworkInterfaceType.Ethernet))
                    {
                        var props = netInterface.GetIPProperties();
                        foreach (var gateway in props.GatewayAddresses)
                        {
                            if (gateway.Address.AddressFamily == AddressFamily.InterNetwork)
                            {
                                var ip = gateway.Address.ToString();
                                if (!string.IsNullOrEmpty(ip) && ip != "0.0.0.0") return ip;
                            }
                        }
                    }
                }
            }
            catch { }
            return "172.30.121.120";
        }

        private void ToggleFullscreen()
        {
            if (!isFullscreen)
            {
                prevWindowState = this.WindowState;
                prevBorderStyle = this.FormBorderStyle;

                topBar.Visible = false;
                this.FormBorderStyle = FormBorderStyle.None;
                this.WindowState = FormWindowState.Maximized;
                isFullscreen = true;
            }
            else
            {
                topBar.Visible = true;
                this.FormBorderStyle = prevBorderStyle;
                this.WindowState = prevWindowState;
                isFullscreen = false;
                Cursor.Show();
            }
        }

        private void MainForm_KeyDown(object? sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.F11)
            {
                ToggleFullscreen();
                e.Handled = true;
            }
            else if (e.KeyCode == Keys.Escape && isFullscreen)
            {
                ToggleFullscreen();
                e.Handled = true;
            }
        }

        private void SetupCursorHiding()
        {
            cursorTimer = new System.Windows.Forms.Timer { Interval = 2500 };
            cursorTimer.Tick += (s, e) =>
            {
                if (isFullscreen && Cursor.Position == lastMousePosition)
                {
                    Cursor.Hide();
                }
            };
            cursorTimer.Start();

            this.MouseMove += (s, e) =>
            {
                Cursor.Show();
                lastMousePosition = Cursor.Position;
            };
        }
    }
}
